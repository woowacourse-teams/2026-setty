package setty.platform.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;

@Embeddable
public class Dimensions {

    private static final int MINIMUM_CENTIMETERS = 1;
    private static final int MAXIMUM_CENTIMETERS = 1_000;

    @Column(name = "width_cm", nullable = false)
    private Integer widthCm;

    @Column(name = "depth_cm", nullable = false)
    private Integer depthCm;

    @Column(name = "height_cm", nullable = false)
    private Integer heightCm;

    protected Dimensions() {
    }

    private Dimensions(final Integer widthCm, final Integer depthCm, final Integer heightCm) {
        validateCentimeters(widthCm);
        validateCentimeters(depthCm);
        validateCentimeters(heightCm);
        this.widthCm = widthCm;
        this.depthCm = depthCm;
        this.heightCm = heightCm;
    }

    public static Dimensions of(final Integer widthCm, final Integer depthCm, final Integer heightCm) {
        return new Dimensions(widthCm, depthCm, heightCm);
    }

    public long volumeCubicCentimeters() {
        return (long) widthCm * depthCm * heightCm;
    }

    private static void validateCentimeters(final Integer centimeters) {
        if (centimeters == null
                || centimeters < MINIMUM_CENTIMETERS
                || centimeters > MAXIMUM_CENTIMETERS) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public Integer getWidthCm() {
        return widthCm;
    }

    public Integer getDepthCm() {
        return depthCm;
    }

    public Integer getHeightCm() {
        return heightCm;
    }
}
