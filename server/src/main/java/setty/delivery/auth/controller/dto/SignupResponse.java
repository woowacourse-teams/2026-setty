package setty.delivery.auth.controller.dto;

import setty.delivery.auth.domain.DeliveryMember;

public record SignupResponse(Long id, String loginId) {

    public static SignupResponse from(final DeliveryMember member) {
        return new SignupResponse(member.getId(), member.getLoginId());
    }
}
