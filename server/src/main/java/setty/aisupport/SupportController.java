package setty.aisupport;

import static setty.aisupport.SupportDtos.*;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import setty.global.auth.LoginMember;

import java.util.Map;

@RestController
@RequestMapping("/api/support/conversations")
@RequiredArgsConstructor
class SupportController {
    private final SupportStore store;
    private final SupportService service;

    @PostMapping
    ResponseEntity<Conversation> create(@LoginMember Long member) {
        return ResponseEntity.status(201).body(store.create(member));
    }

    @GetMapping
    ConversationPage list(@LoginMember Long member, @RequestParam(required = false) String cursor) {
        return store.list(member, cursor);
    }

    @GetMapping("/{id}")
    Detail detail(
            @LoginMember Long member,
            @PathVariable long id,
            @RequestParam(required = false) Long cursor) {
        cursor(cursor);
        return store.detail(member, id, cursor);
    }

    @PostMapping("/{id}/messages")
    Turn send(@LoginMember Long member, @PathVariable long id, @RequestBody SendRequest request) {
        return service.send(member, id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@LoginMember Long member, @PathVariable long id) {
        store.delete(member, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(SupportException.class)
    ResponseEntity<Map<String, String>> error(SupportException e) {
        return ResponseEntity.status(e.status)
                .body(OrderedJson.of("code", e.code, "message", e.getMessage()));
    }

    private void cursor(Long cursor) {
        if (cursor != null && cursor <= 0)
            throw new SupportException(400, "INVALID_CURSOR", "페이지 정보를 확인해 주세요.");
    }
}
