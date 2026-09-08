package com.silvertown.domain.recipient.dto;
import java.util.Collections;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class RecipientCandidateRequest {
    @NotBlank @Size(max = 100) private String keyword;
    @Valid @Size(max = 200) private List<ContactInput> contacts = Collections.emptyList();
    @Getter @Setter
    public static class ContactInput {
        @NotBlank @Size(max = 100) private String displayName;
        @Size(max = 30) private String phoneNumber;
    }
}
