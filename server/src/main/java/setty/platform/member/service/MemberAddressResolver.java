package setty.platform.member.service;

import org.springframework.stereotype.Component;
import setty.address.AddressResolution;
import setty.address.AddressService;
import setty.address.StandardAddress;
import setty.deliveryfee.DeliveryFeeService;
import setty.platform.member.domain.Member;
import setty.platform.member.domain.ResolvedAddress;

@Component
public class MemberAddressResolver {

    private final AddressService addressService;
    private final DeliveryFeeService deliveryFeeService;

    public MemberAddressResolver(final AddressService addressService, final DeliveryFeeService deliveryFeeService) {
        this.addressService = addressService;
        this.deliveryFeeService = deliveryFeeService;
    }

    // 해석에 실패해도 상태만 남긴다. 회원이 고른 행정동은 덮어쓰지 않는다.
    public void resolve(final Member member) {
        final AddressResolution resolution = addressService.resolve(member.getAddress());
        member.changeResolvedAddress(ResolvedAddress.from(resolution));
        final StandardAddress address = resolution.address();
        if (member.getDongCode() == null && address != null && address.dongCode() != null
                && deliveryFeeService.findDong(address.dongCode()).isPresent()) {
            member.changeDong(address.dongCode());
        }
    }
}
