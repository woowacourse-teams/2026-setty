package setty.dispatch.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import setty.dispatch.dto.buyer.BuyerDispatchRequestCreateRequest;
import setty.dispatch.dto.buyer.BuyerDispatchRequestCreateResponse;
import setty.dispatch.dto.buyer.BuyerDispatchRequestResponse;
import setty.dispatch.dto.buyer.DispatchItemImageResponse;
import setty.dispatch.service.BuyerDispatchRequestService;
import setty.dispatch.service.ItemImageStorage;

@RestController
@RequestMapping("/api/dispatch-requests")
public class BuyerDispatchRequestController {
    private final BuyerDispatchRequestService buyerDispatchRequestService;
    private final ItemImageStorage itemImageStorage;

    public BuyerDispatchRequestController(
            final BuyerDispatchRequestService buyerDispatchRequestService,
            final ItemImageStorage itemImageStorage
    ) {
        this.buyerDispatchRequestService = buyerDispatchRequestService;
        this.itemImageStorage = itemImageStorage;
    }

    @PostMapping
    public ResponseEntity<BuyerDispatchRequestCreateResponse> create(
            @Valid @RequestBody final BuyerDispatchRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(buyerDispatchRequestService.create(request));
    }

    @GetMapping("/{buyerToken}")
    public ResponseEntity<BuyerDispatchRequestResponse> findByBuyerToken(@PathVariable final String buyerToken) {
        return ResponseEntity.ok(buyerDispatchRequestService.findByBuyerToken(buyerToken));
    }

    @PostMapping("/{buyerToken}/approval")
    public ResponseEntity<Void> approveFinalAmount(@PathVariable final String buyerToken) {
        buyerDispatchRequestService.approveFinalAmount(buyerToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/images")
    public ResponseEntity<DispatchItemImageResponse> uploadItemImage(
            @RequestParam("image") final MultipartFile image
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DispatchItemImageResponse(itemImageStorage.store(image)));
    }
}
