package setty.deliveryfee.region;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

// 일방통행 때문에 A→B와 B→A가 다르므로 행은 판매자 동, 열은 구매자 동으로 고정해 읽는다.
@Component
public class DistanceTable {

    private static final String RESOURCE = "deliveryfee/seoul-dong-distances.bin";

    private final int[] meters;
    private final int size;

    public DistanceTable() {
        final ByteBuffer buffer = ByteBuffer.wrap(read()).order(ByteOrder.LITTLE_ENDIAN);
        this.meters = new int[buffer.remaining() / Integer.BYTES];
        buffer.asIntBuffer().get(meters);
        this.size = (int) Math.round(Math.sqrt(meters.length));
        if (size * size != meters.length) {
            throw new IllegalStateException("거리표가 정사각 행렬이 아닙니다: " + meters.length);
        }
    }

    public int meters(final int sellerDong, final int buyerDong) {
        return meters[sellerDong * size + buyerDong];
    }

    public int size() {
        return size;
    }

    private static byte[] read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return in.readAllBytes();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
