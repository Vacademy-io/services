package vacademy.io.notification_service.features.chatbot_flow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vacademy.io.notification_service.features.chatbot_flow.controller.WhatsAppInboxController;
import vacademy.io.notification_service.features.chatbot_flow.dto.InboxMessageDTO;
import vacademy.io.notification_service.features.chatbot_flow.dto.InboxSendRequest;
import vacademy.io.notification_service.features.chatbot_flow.service.ChatbotEscalationService;
import vacademy.io.notification_service.features.chatbot_flow.service.WhatsAppInboxService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The wire contract of {@code POST /inbox/send}, exercised with the exact JSON the admin Inbox
 * sends.
 *
 * This is the seam the service-level tests cannot see. The endpoint used to take a
 * {@code Map<String,String>} and now binds a DTO, so two things have to stay true at once: an
 * older client that posts only phone/text/instituteId must keep working untouched, and a body
 * carrying mediaType + mediaUrl must reach the media path rather than being quietly dropped and
 * delivered as text — which is exactly what an un-updated backend does with these fields.
 */
class WhatsAppInboxSendContractTest {

    private static final String INSTITUTE = "54d0a67f-8a13-4137-872d-f62d68ef7971";
    private static final String PHONE = "919682419977";

    private WhatsAppInboxService inboxService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        inboxService = mock(WhatsAppInboxService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WhatsAppInboxController(inboxService, mock(ChatbotEscalationService.class)))
                .build();
    }

    private InboxMessageDTO reply() {
        return InboxMessageDTO.builder().id("log-1").direction("OUTGOING").source("INBOX").build();
    }

    @Test
    @DisplayName("the pre-media client's body still routes to the plain text send")
    void textOnlyBodyIsUnchanged() throws Exception {
        when(inboxService.sendReply(anyString(), anyString(), anyString(), any()))
                .thenReturn(reply());

        mockMvc.perform(post("/notification-service/v1/inbox/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","text":"hello","instituteId":"%s"}
                                """.formatted(PHONE, INSTITUTE)))
                .andExpect(status().isOk());

        verify(inboxService).sendReply(PHONE, "hello", INSTITUTE, null);
        verify(inboxService, never()).sendMediaReply(any());
    }

    @Test
    @DisplayName("mediaType + mediaUrl reach the media path, with the text as the caption")
    void mediaBodyRoutesToMediaSend() throws Exception {
        when(inboxService.sendMediaReply(any())).thenReturn(reply());

        mockMvc.perform(post("/notification-service/v1/inbox/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","text":"Here is your brochure","instituteId":"%s",
                                 "mediaType":"image","mediaUrl":"https://cdn.example.com/b.png",
                                 "filename":"b.png"}
                                """.formatted(PHONE, INSTITUTE)))
                .andExpect(status().isOk());

        ArgumentCaptor<InboxSendRequest> sent = ArgumentCaptor.forClass(InboxSendRequest.class);
        verify(inboxService).sendMediaReply(sent.capture());
        assertThat(sent.getValue().getMediaType()).isEqualTo("image");
        assertThat(sent.getValue().getMediaUrl()).isEqualTo("https://cdn.example.com/b.png");
        assertThat(sent.getValue().getFilename()).isEqualTo("b.png");
        assertThat(sent.getValue().getText()).isEqualTo("Here is your brochure");
        verify(inboxService, never()).sendReply(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("an attachment with no caption is still a media send, not a rejected empty message")
    void captionlessMediaIsAccepted() throws Exception {
        when(inboxService.sendMediaReply(any())).thenReturn(reply());

        mockMvc.perform(post("/notification-service/v1/inbox/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","instituteId":"%s","mediaType":"audio",
                                 "mediaUrl":"https://cdn.example.com/voice-note.m4a"}
                                """.formatted(PHONE, INSTITUTE)))
                .andExpect(status().isOk());

        verify(inboxService).sendMediaReply(any());
    }

    @Test
    @DisplayName("force rides through to the service for a 72h or webhook-gap conversation")
    void forceIsCarried() throws Exception {
        when(inboxService.sendMediaReply(any())).thenReturn(reply());

        mockMvc.perform(post("/notification-service/v1/inbox/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","instituteId":"%s","mediaType":"image",
                                 "mediaUrl":"https://cdn.example.com/a.png","force":true}
                                """.formatted(PHONE, INSTITUTE)))
                .andExpect(status().isOk());

        ArgumentCaptor<InboxSendRequest> sent = ArgumentCaptor.forClass(InboxSendRequest.class);
        verify(inboxService).sendMediaReply(sent.capture());
        assertThat(sent.getValue().isForce()).isTrue();
    }

    @Test
    @DisplayName("a field this backend does not know is ignored, not a 400")
    void unknownFieldsDoNotBreakBinding() throws Exception {
        when(inboxService.sendReply(anyString(), anyString(), anyString(), any()))
                .thenReturn(reply());

        // A newer client may send fields this build predates. Binding must not reject the whole
        // request over one of them.
        mockMvc.perform(post("/notification-service/v1/inbox/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","text":"hi","instituteId":"%s","somethingNew":"x"}
                                """.formatted(PHONE, INSTITUTE)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("neither text nor media is a bad request, not an empty WhatsApp message")
    void emptyMessageIsRejected() throws Exception {
        mockMvc.perform(post("/notification-service/v1/inbox/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"phone":"%s","instituteId":"%s"}
                                """.formatted(PHONE, INSTITUTE)))
                .andExpect(status().isBadRequest());

        verify(inboxService, never()).sendMediaReply(any());
        verify(inboxService, never()).sendReply(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("the session window is reported for the composer to gate on")
    void sessionWindowIsExposed() throws Exception {
        when(inboxService.sessionWindow(PHONE, INSTITUTE)).thenReturn(
                vacademy.io.notification_service.features.chatbot_flow.dto.SessionWindowDTO.builder()
                        .open(true).minutesRemaining(120L).unknown(false).build());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/notification-service/v1/inbox/conversations/{phone}/session-window", PHONE)
                        .param("instituteId", INSTITUTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.minutesRemaining").value(120));
    }
}
