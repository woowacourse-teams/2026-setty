package setty.prototype.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import setty.prototype.dto.seller.SellerPageResponse;
import setty.prototype.service.SellerPageService;
import setty.prototype.web.LoginMemberId;

@RestController
@RequestMapping("/api/me")
public class SellerPageController {
    private final SellerPageService sellerPageService;

    public SellerPageController(final SellerPageService sellerPageService) {
        this.sellerPageService = sellerPageService;
    }

    @GetMapping("/seller-page")
    public ResponseEntity<SellerPageResponse> findSellerPage(@LoginMemberId final Long memberId) {
        return ResponseEntity.ok(sellerPageService.findByMemberId(memberId));
    }
}
