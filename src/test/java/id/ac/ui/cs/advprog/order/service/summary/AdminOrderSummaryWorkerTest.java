package id.ac.ui.cs.advprog.order.service.summary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminOrderSummaryWorkerTest {
    @Mock
    private AdminOrderSummaryMaterializer materializer;

    @InjectMocks
    private AdminOrderSummaryWorker worker;

    @Test
    void refreshSummarySnapshotShouldCallMaterializer() {
        worker.refreshSummarySnapshot();
        verify(materializer).refresh();
    }

    @Test
    void refreshSummarySnapshotShouldSwallowRuntimeException() {
        doThrow(new IllegalStateException("failed")).when(materializer).refresh();
        assertDoesNotThrow(() -> worker.refreshSummarySnapshot());
    }
}
