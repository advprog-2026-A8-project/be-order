param(
    [string]$AuthBase = "http://localhost:3002",
    [string]$OrderBase = "http://localhost:5002",
    [string]$InventoryBase = "http://localhost:4002",
    [string]$WalletBase = "http://localhost:6002",
    [string]$AdminEmail = "asdos@example.com",
    [string]$AdminPassword = "admin123",
    [string]$TitiperEmail = "titip@example.com",
    [string]$TitiperPassword = "Titip123!",
    [string]$JastiperEmail = "jastip@example.com",
    [string]$JastiperPassword = "Jastip123!",
    [double]$TopupAmount = 200000000,
    [int]$PayloadRows = 12,
    [int]$GeneratedProductStock = 10000,
    [switch]$SkipTopup
)

$ErrorActionPreference = "Stop"

$dataDir = Join-Path $PSScriptRoot "..\data"
$adminCsvPath = Join-Path $dataDir "admin_tokens.csv"
$titiperCsvPath = Join-Path $dataDir "titiper_tokens.csv"
$jastiperCsvPath = Join-Path $dataDir "jastiper_tokens.csv"
$payloadCsvPath = Join-Path $dataDir "checkout_payloads.csv"
$bizCsvPath = Join-Path $dataDir "business_order_ids.csv"

function Login-User {
    param(
        [string]$Email,
        [string]$Password,
        [string]$BaseUrl
    )

    $body = @{ email = $Email; password = $Password } | ConvertTo-Json
    return Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/login" -ContentType "application/json" -Body $body
}

function New-CheckoutOrder {
    param(
        [string]$OrderApi,
        [string]$Token,
        [string]$ProductId,
        [string]$TitiperUserId,
        [string]$JastiperUserId,
        [int]$Jumlah,
        [string]$Alamat,
        [string]$VoucherCode
    )

    $body = @{
        productId = $ProductId
        userId = $TitiperUserId
        jastiperId = $JastiperUserId
        jumlah = $Jumlah
        alamatPengiriman = $Alamat
        voucherCode = $VoucherCode
    } | ConvertTo-Json

    $headers = @{
        Authorization = $Token
        "Content-Type" = "application/json"
        "Idempotency-Key" = [guid]::NewGuid().ToString()
    }

    try {
        return Invoke-RestMethod -Method POST -Uri "$OrderApi/api/orders/checkout" -Headers $headers -Body $body
    } catch {
        $responseBody = $null
        if ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $responseBody = $reader.ReadToEnd()
                }
            } catch {
                $responseBody = $null
            }
        }

        if ($responseBody) {
            throw "Checkout gagal. Response body: $responseBody"
        }
        throw "Checkout gagal. Error: $($_.Exception.Message)"
    }
}

function Ensure-Product {
    param(
        [string]$InventoryApi,
        [string]$JastiperUserId,
        [string]$JastiperToken,
        [string]$ProductIdFromCsv
    )

    if ($ProductIdFromCsv -and $ProductIdFromCsv.Trim() -ne "") {
        try {
            $existing = Invoke-RestMethod -Method GET -Uri "$InventoryApi/api/products/$ProductIdFromCsv"
            if ($existing.stock -gt 0 -and $existing.price -gt 0) {
                return $existing.id
            }
        } catch {
            Write-Host "Product ID di CSV tidak valid, mencari product lain..."
        }
    }

    $products = Invoke-RestMethod -Method GET -Uri "$InventoryApi/api/products/jastiper/$JastiperUserId"
    $valid = $products | Where-Object { $_.stock -gt 0 -and $_.price -gt 0 } | Select-Object -First 1
    if ($valid) {
        return $valid.id
    }

    Write-Host "Belum ada product valid milik jastiper, membuat product baru..."
    $createBody = @{
        name = "Produk Perf JMeter"
        description = "Auto-generated product for order performance test"
        price = 100000
        stock = 200
        originCountry = "Jepang"
        purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
    } | ConvertTo-Json

    $created = Invoke-RestMethod -Method POST -Uri "$InventoryApi/api/products/create" -Headers @{
        Authorization = $JastiperToken
        "Content-Type" = "application/json"
    } -Body $createBody

    return $created.id
}

function Get-ValidProducts {
    param(
        [string]$InventoryApi,
        [string]$JastiperUserId
    )

    $products = Invoke-RestMethod -Method GET -Uri "$InventoryApi/api/products/jastiper/$JastiperUserId"
    return @($products | Where-Object { $_.stock -gt 0 -and $_.price -gt 0 })
}

function New-LoadTestProduct {
    param(
        [string]$InventoryApi,
        [string]$JastiperToken,
        [int]$Stock,
        [int]$Index
    )

    $createBody = @{
        name = "Produk Perf JMeter $Index"
        description = "Auto-generated product for order performance test"
        price = 100000
        stock = $Stock
        originCountry = "Jepang"
        purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
    } | ConvertTo-Json

    return Invoke-RestMethod -Method POST -Uri "$InventoryApi/api/products/create" -Headers @{
        Authorization = $JastiperToken
        "Content-Type" = "application/json"
    } -Body $createBody
}

Write-Host "Login admin/titiper/jastiper..."
$adminLogin = Login-User -Email $AdminEmail -Password $AdminPassword -BaseUrl $AuthBase
$titiperLogin = Login-User -Email $TitiperEmail -Password $TitiperPassword -BaseUrl $AuthBase
$jastiperLogin = Login-User -Email $JastiperEmail -Password $JastiperPassword -BaseUrl $AuthBase

if ($jastiperLogin.data.role -ne "JASTIPER") {
    throw "Akun jastiper belum role JASTIPER. Role saat ini: $($jastiperLogin.data.role)"
}

$adminToken = "Bearer " + $adminLogin.data.token
$titiperUserId = $titiperLogin.data.userId
$titiperToken = "Bearer " + $titiperLogin.data.token
$jastiperUserId = $jastiperLogin.data.userId
$jastiperToken = "Bearer " + $jastiperLogin.data.token

$existingPayload = $null
if (Test-Path $payloadCsvPath) {
    $existingPayload = Import-Csv $payloadCsvPath | Select-Object -First 1
}

$productId = Ensure-Product -InventoryApi $InventoryBase -JastiperUserId $jastiperUserId -JastiperToken $jastiperToken -ProductIdFromCsv $existingPayload.productId
$jumlah = if ($existingPayload.jumlah) { [int]$existingPayload.jumlah } else { 1 }
$alamat = if ($existingPayload.alamatPengiriman) { $existingPayload.alamatPengiriman } else { "Jalan Margonda Raya" }
$voucherCode = if ($existingPayload.voucherCode) { $existingPayload.voucherCode } else { "" }

$validProducts = Get-ValidProducts -InventoryApi $InventoryBase -JastiperUserId $jastiperUserId
if ($validProducts.Count -eq 0) {
    throw "Tidak ada produk valid untuk load test setelah proses ensure product."
}

$productMap = @{}
foreach ($p in $validProducts) {
    if ($null -ne $p.id -and $p.id -ne "") {
        $productMap[$p.id] = $p
    }
}

if (-not $productMap.ContainsKey($productId)) {
    $productMap[$productId] = [PSCustomObject]@{
        id = $productId
        stock = 1
        price = 100000
    }
}

while ($productMap.Count -lt $PayloadRows) {
    $newIndex = $productMap.Count + 1
    Write-Host "Membuat produk tambahan untuk payload load test... ($newIndex)"
    $created = New-LoadTestProduct -InventoryApi $InventoryBase -JastiperToken $jastiperToken -Stock $GeneratedProductStock -Index $newIndex
    if ($null -ne $created.id -and $created.id -ne "") {
        $productMap[$created.id] = $created
    }
}

$payloadProducts = @($productMap.Values | Sort-Object stock -Descending)
if ($payloadProducts.Count -gt $PayloadRows) {
    $payloadProducts = $payloadProducts | Select-Object -First $PayloadRows
}

if (-not $SkipTopup) {
    try {
        Write-Host "Top up saldo titiper..."
        $topupBody = @{ userId = $titiperUserId; amount = $TopupAmount } | ConvertTo-Json
        Invoke-RestMethod -Method POST -Uri "$WalletBase/wallet/topup" -Headers @{
            Authorization = $titiperToken
            "Content-Type" = "application/json"
        } -Body $topupBody | Out-Null
    } catch {
        Write-Warning "Top up gagal. Lanjut tanpa topup. Pastikan saldo titiper cukup. Error: $($_.Exception.Message)"
    }
}

Write-Host "Membuat order untuk skenario status/cancel/rating..."
$oStatus = New-CheckoutOrder -OrderApi $OrderBase -Token $titiperToken -ProductId $productId -TitiperUserId $titiperUserId -JastiperUserId $jastiperUserId -Jumlah $jumlah -Alamat $alamat -VoucherCode $voucherCode
$oCancel = New-CheckoutOrder -OrderApi $OrderBase -Token $titiperToken -ProductId $productId -TitiperUserId $titiperUserId -JastiperUserId $jastiperUserId -Jumlah $jumlah -Alamat $alamat -VoucherCode $voucherCode
$oCompleted = New-CheckoutOrder -OrderApi $OrderBase -Token $titiperToken -ProductId $productId -TitiperUserId $titiperUserId -JastiperUserId $jastiperUserId -Jumlah $jumlah -Alamat $alamat -VoucherCode $voucherCode

$adminHeaders = @{ Authorization = $adminToken }
Invoke-RestMethod -Method PATCH -Uri "$OrderBase/api/orders/$($oCompleted.id)/status?status=PURCHASED" -Headers $adminHeaders | Out-Null
Invoke-RestMethod -Method PATCH -Uri "$OrderBase/api/orders/$($oCompleted.id)/status?status=SHIPPED" -Headers $adminHeaders | Out-Null
Invoke-RestMethod -Method PATCH -Uri "$OrderBase/api/orders/$($oCompleted.id)/status?status=COMPLETED" -Headers $adminHeaders | Out-Null

Set-Content -Path $adminCsvPath -Value "adminToken`n$adminToken"
Set-Content -Path $titiperCsvPath -Value "titiperUserId,titiperToken`n$titiperUserId,$titiperToken"
Set-Content -Path $jastiperCsvPath -Value "jastiperUserId,jastiperToken`n$jastiperUserId,$jastiperToken"

$payloadLines = @("productId,jumlah,alamatPengiriman,voucherCode")
foreach ($product in $payloadProducts) {
    $payloadLines += "$($product.id),$jumlah,$alamat,$voucherCode"
}
Set-Content -Path $payloadCsvPath -Value ($payloadLines -join "`n")

Set-Content -Path $bizCsvPath -Value "statusOrderId,nextStatus,cancelOrderId,completedOrderId,jastiperIdForCancel,jastiperRating,productRating`n$($oStatus.id),PURCHASED,$($oCancel.id),$($oCompleted.id),$jastiperUserId,5,5"

Write-Host "Selesai. CSV phase-2 berhasil diregenerasi."
Write-Host "statusOrderId   = $($oStatus.id)"
Write-Host "cancelOrderId   = $($oCancel.id)"
Write-Host "completedOrderId= $($oCompleted.id)"
Write-Host "productId       = $productId"
Write-Host "payloadRows     = $($payloadProducts.Count)"
