package setty.platform.member.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateDongRequest(@NotBlank String dongCode) {
}
