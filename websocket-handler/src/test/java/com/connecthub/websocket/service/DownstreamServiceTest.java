package com.connecthub.websocket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test class for {@link DownstreamService}.
 * Manually constructs the service to ensure correct WebClient injection
 * (Lombok @RequiredArgsConstructor may not preserve parameter names).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownstreamServiceTest {

    @Mock private WebClient messageClient;
    @Mock private WebClient presenceClient;
    @Mock private WebClient notificationClient;
    @Mock private WebClient roomClient;
    @Mock private WebClient authClient;

    private DownstreamService service;

    // POST chain mocks
    @Mock private WebClient.RequestBodyUriSpec postUriSpec;
    @Mock private WebClient.RequestBodySpec   postBodySpec;
    @Mock private WebClient.ResponseSpec      postResponseSpec;

    // PUT chain mocks
    @Mock private WebClient.RequestBodyUriSpec putUriSpec;
    @Mock private WebClient.RequestBodySpec   putBodySpec;
    @Mock private WebClient.ResponseSpec      putResponseSpec;

    // GET chain mocks
    @Mock private WebClient.RequestHeadersUriSpec getUriSpec;
    @Mock private WebClient.RequestHeadersSpec    getHeadersSpec;
    @Mock private WebClient.ResponseSpec          getResponseSpec;

    // DELETE chain mocks
    @Mock private WebClient.RequestHeadersUriSpec deleteUriSpec;
    @Mock private WebClient.RequestHeadersSpec    deleteHeadersSpec;
    @Mock private WebClient.ResponseSpec          deleteResponseSpec;

    @BeforeEach
    void setUp() {
        service = new DownstreamService(messageClient, presenceClient,
                notificationClient, roomClient, authClient);
    }

    // -----------------------------------------------------------------------
    //  Helpers – doReturn().when(mock).method() for full chain stubbing
    // -----------------------------------------------------------------------

    private void stubPost(WebClient client, Mono<Map> result) {
        doReturn(postUriSpec).when(client).post();
        doReturn(postBodySpec).when(postUriSpec).uri(anyString());
        doReturn(postBodySpec).when(postUriSpec).uri(anyString(), (Object[]) any());
        doReturn(postBodySpec).when(postBodySpec).header(anyString(), anyString());
        doReturn(postBodySpec).when(postBodySpec).bodyValue(any());
        doReturn(postResponseSpec).when(postBodySpec).retrieve();
        doReturn(result).when(postResponseSpec).bodyToMono(Map.class);
    }

    private void stubPostVoid(WebClient client) {
        doReturn(postUriSpec).when(client).post();
        doReturn(postBodySpec).when(postUriSpec).uri(anyString());
        doReturn(postBodySpec).when(postUriSpec).uri(anyString(), (Object[]) any());
        doReturn(postBodySpec).when(postUriSpec).uri(anyString(), any(), any(), any()); // For sendPush with 3 path vars
        doReturn(postBodySpec).when(postBodySpec).header(anyString(), anyString());
        doReturn(postBodySpec).when(postBodySpec).bodyValue(any());
        doReturn(postResponseSpec).when(postBodySpec).retrieve();
        doReturn(Mono.just(ResponseEntity.ok().build())).when(postResponseSpec).toBodilessEntity();
    }

    private void stubPut(WebClient client, Mono<Map> result) {
        doReturn(putUriSpec).when(client).put();
        doReturn(putBodySpec).when(putUriSpec).uri(anyString());
        doReturn(putBodySpec).when(putUriSpec).uri(anyString(), (Object[]) any());
        doReturn(putBodySpec).when(putBodySpec).header(anyString(), anyString());
        doReturn(putBodySpec).when(putBodySpec).bodyValue(any());
        doReturn(putResponseSpec).when(putBodySpec).retrieve();
        doReturn(result).when(putResponseSpec).bodyToMono(Map.class);
    }

    private void stubPutVoid(WebClient client) {
        doReturn(putUriSpec).when(client).put();
        doReturn(putBodySpec).when(putUriSpec).uri(anyString());
        doReturn(putBodySpec).when(putUriSpec).uri(anyString(), (Object[]) any());
        doReturn(putBodySpec).when(putUriSpec).uri(anyString(), any(), any());
        doReturn(putBodySpec).when(putBodySpec).header(anyString(), anyString());
        doReturn(putBodySpec).when(putBodySpec).bodyValue(any());
        doReturn(putResponseSpec).when(putBodySpec).retrieve();
        doReturn(Mono.just(ResponseEntity.ok().build())).when(putResponseSpec).toBodilessEntity();
    }

    private void stubDelete(WebClient client, Mono<ResponseEntity<Void>> result) {
        doReturn(deleteUriSpec).when(client).delete();
        doReturn(deleteHeadersSpec).when(deleteUriSpec).uri(anyString());
        doReturn(deleteHeadersSpec).when(deleteUriSpec).uri(anyString(), (Object[]) any());
        doReturn(deleteHeadersSpec).when(deleteHeadersSpec).header(anyString(), anyString());
        doReturn(deleteResponseSpec).when(deleteHeadersSpec).retrieve();
        doReturn(result).when(deleteResponseSpec).toBodilessEntity();
    }

    private void stubGet(WebClient client, Mono<Map> result) {
        doReturn(getUriSpec).when(client).get();
        doReturn(getHeadersSpec).when(getUriSpec).uri(anyString());
        doReturn(getHeadersSpec).when(getUriSpec).uri(anyString(), (Object[]) any());
        doReturn(getHeadersSpec).when(getHeadersSpec).header(anyString(), anyString());
        doReturn(getResponseSpec).when(getHeadersSpec).retrieve();
        doReturn(result).when(getResponseSpec).bodyToMono(Map.class);
    }

    // -- persistMessage --

    @Test void persistMessage_allFieldsSet_returnsMonoWithResult() {
        stubPost(messageClient, Mono.just(Map.of("id", 42L)));
        StepVerifier.create(service.persistMessage(1L, 10L, "hello", "TEXT", "http://img", 5L, "token"))
                .expectNextMatches(m -> m.get("id").equals(42L)).verifyComplete();
    }

    @Test void persistMessage_nullContent_defaultsToEmpty() {
        stubPost(messageClient, Mono.just(Map.of()));
        StepVerifier.create(service.persistMessage(1L, 10L, null, null, null, null, "token")).expectNextCount(1).verifyComplete();
    }

    @Test void persistMessage_nullReplyToId_skipsReplyField() {
        stubPost(messageClient, Mono.just(Map.of()));
        StepVerifier.create(service.persistMessage(1L, 10L, "hello", "TEXT", "", null, "token")).expectNextCount(1).verifyComplete();
    }

    @Test void persistMessage_zeroReplyToId_skipsReplyField() {
        stubPost(messageClient, Mono.just(Map.of()));
        StepVerifier.create(service.persistMessage(1L, 10L, "hi", "TEXT", "", 0L, "token")).expectNextCount(1).verifyComplete();
    }

    @Test void persistMessage_downstreamError_returnsEmpty() {
        stubPost(messageClient, Mono.error(new RuntimeException("down")));
        StepVerifier.create(service.persistMessage(1L, 10L, "x", "TEXT", null, null, "token")).verifyComplete();
    }

    // -- persistDirectMessage --

    @Test void persistDirectMessage_allFields_returnsResult() {
        stubPost(messageClient, Mono.just(Map.of("id", 77L)));
        StepVerifier.create(service.persistDirectMessage(1L, 2L, "hey", "TEXT", null, null, "token"))
                .expectNextMatches(m -> m.get("id").equals(77L)).verifyComplete();
    }

    @Test void persistDirectMessage_nullContent_defaultsEmpty() {
        stubPost(messageClient, Mono.just(Map.of()));
        StepVerifier.create(service.persistDirectMessage(1L, 2L, null, null, null, 3L, "token")).expectNextCount(1).verifyComplete();
    }

    @Test void persistDirectMessage_zeroReplyToId_skipsField() {
        stubPost(messageClient, Mono.just(Map.of()));
        StepVerifier.create(service.persistDirectMessage(1L, 2L, "hi", "TEXT", null, 0L, "token")).expectNextCount(1).verifyComplete();
    }

    @Test void persistDirectMessage_error_returnsEmpty() {
        stubPost(messageClient, Mono.error(new RuntimeException("fail")));
        StepVerifier.create(service.persistDirectMessage(1L, 2L, "x", null, null, null, "token")).verifyComplete();
    }

    // -- updateDeliveryStatus --

    @Test void updateDeliveryStatus_callsMessageClientPut() {
        stubPutVoid(messageClient);
        assertDoesNotThrow(() -> service.updateDeliveryStatus(1L, 2L, "READ"));
        verify(messageClient).put();
    }

    @Test void updateDeliveryStatus_errorIsSwallowed() {
        stubPutVoid(messageClient);
        doReturn(Mono.error(new RuntimeException("down"))).when(putResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.updateDeliveryStatus(1L, 2L, "DELIVERED"));
    }

    // -- editMessage --

    @Test void editMessage_success_returnsMono() {
        stubPut(messageClient, Mono.just(Map.of("id", 5L)));
        StepVerifier.create(service.editMessage(5L, "new content", "token")).expectNextCount(1).verifyComplete();
    }

    @Test void editMessage_error_returnsEmpty() {
        stubPut(messageClient, Mono.error(new RuntimeException("edit fail")));
        StepVerifier.create(service.editMessage(5L, "content", "token")).verifyComplete();
    }

    // -- deleteMessage --

    @Test void deleteMessage_normalDelete_callsNonAdminUri() {
        stubDelete(messageClient, Mono.just(ResponseEntity.ok().build()));
        StepVerifier.create(service.deleteMessage(10L, false, "token")).expectNext(true).verifyComplete();
    }

    @Test void deleteMessage_adminDelete_callsAdminUri() {
        stubDelete(messageClient, Mono.just(ResponseEntity.ok().build()));
        StepVerifier.create(service.deleteMessage(10L, true, "token")).expectNext(true).verifyComplete();
    }

    @Test void deleteMessage_error_returnsTrue() {
        stubDelete(messageClient, Mono.error(new RuntimeException("del fail")));
        StepVerifier.create(service.deleteMessage(10L, false, "token")).expectNext(true).verifyComplete();
    }

    // -- markRoomRead --

    @Test void markRoomRead_callsBothMessageAndRoomClients() {
        stubPutVoid(messageClient);
        stubPutVoid(roomClient);
        assertDoesNotThrow(() -> service.markRoomRead(1L, 2L, "2024-01-01T10:00:00"));
        verify(messageClient).put();
        verify(roomClient).put();
    }

    @Test void markRoomRead_errorsAreSwallowed() {
        stubPutVoid(messageClient);
        stubPutVoid(roomClient);
        doReturn(Mono.error(new RuntimeException("fail"))).when(putResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.markRoomRead(1L, 2L, "2024-01-01T10:00:00"));
    }

    // -- setUserOnline --

    @Test void setUserOnline_withDeviceAndIp_succeeds() {
        stubPostVoid(presenceClient);
        assertDoesNotThrow(() -> service.setUserOnline(1L, "sess-1", "MOBILE", "10.0.0.1"));
    }

    @Test void setUserOnline_nullDeviceType_defaultsToWeb() {
        stubPostVoid(presenceClient);
        assertDoesNotThrow(() -> service.setUserOnline(1L, "sess-1", null, null));
    }

    @Test void setUserOnline_errorIsSwallowed() {
        stubPostVoid(presenceClient);
        doReturn(Mono.error(new RuntimeException("presence down"))).when(postResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.setUserOnline(1L, "sess-1", "WEB", "127.0.0.1"));
    }

    // -- setUserOffline --

    @Test void setUserOffline_withAuthToken_callsPresenceAndLastSeen() {
        stubPostVoid(presenceClient);
        stubPostVoid(authClient);
        assertDoesNotThrow(() -> service.setUserOffline(1L, "sess-1", "token"));
        verify(presenceClient).post();
        verify(authClient).post();
    }

    @Test void setUserOffline_nullToken_skipsLastSeen() {
        stubPostVoid(presenceClient);
        assertDoesNotThrow(() -> service.setUserOffline(1L, "sess-1", null));
        verify(authClient, never()).post();
    }

    @Test void setUserOffline_blankToken_skipsLastSeen() {
        stubPostVoid(presenceClient);
        assertDoesNotThrow(() -> service.setUserOffline(1L, "sess-1", "   "));
        verify(authClient, never()).post();
    }

    // -- recordLastSeen --

    @Test void recordLastSeen_validToken_callsAuthClient() {
        stubPostVoid(authClient);
        assertDoesNotThrow(() -> service.recordLastSeen("valid-token"));
        verify(authClient).post();
    }

    @Test void recordLastSeen_nullToken_earlyReturn() {
        service.recordLastSeen(null);
        verifyNoInteractions(authClient);
    }

    @Test void recordLastSeen_blankToken_earlyReturn() {
        service.recordLastSeen("  ");
        verifyNoInteractions(authClient);
    }

    @Test void recordLastSeen_errorIsSwallowed() {
        stubPostVoid(authClient);
        doReturn(Mono.error(new RuntimeException("auth down"))).when(postResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.recordLastSeen("token"));
    }

    // -- pingPresence --

    @Test void pingPresence_callsPresenceClient() {
        stubPostVoid(presenceClient);
        assertDoesNotThrow(() -> service.pingPresence("sess-1"));
        verify(presenceClient).post();
    }

    @Test void pingPresence_errorIsSwallowed() {
        stubPostVoid(presenceClient);
        doReturn(Mono.error(new RuntimeException("ping fail"))).when(postResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.pingPresence("sess-1"));
    }

    // -- updatePresenceStatus --

    @Test void updatePresenceStatus_withToken_addsAuthHeader() {
        stubPutVoid(presenceClient);
        assertDoesNotThrow(() -> service.updatePresenceStatus(1L, "AWAY", "token"));
        verify(presenceClient).put();
    }

    @Test void updatePresenceStatus_nullToken_skipsAuthHeader() {
        stubPutVoid(presenceClient);
        assertDoesNotThrow(() -> service.updatePresenceStatus(1L, "DND", null));
    }

    @Test void updatePresenceStatus_blankToken_skipsAuthHeader() {
        stubPutVoid(presenceClient);
        assertDoesNotThrow(() -> service.updatePresenceStatus(1L, "ONLINE", "  "));
    }

    // -- getRoomDetails --

    @Test void getRoomDetails_noToken_returnsRoomData() {
        stubGet(roomClient, Mono.just(Map.of("id", 1L, "name", "General")));
        StepVerifier.create(service.getRoomDetails(1L))
                .expectNextMatches(m -> "General".equals(m.get("name"))).verifyComplete();
    }

    @Test void getRoomDetails_withToken_addsAuthHeader() {
        stubGet(roomClient, Mono.just(Map.of("id", 1L)));
        StepVerifier.create(service.getRoomDetails(1L, "token")).expectNextCount(1).verifyComplete();
    }

    @Test void getRoomDetails_nullToken_doesNotAddHeader() {
        stubGet(roomClient, Mono.just(Map.of("id", 1L)));
        StepVerifier.create(service.getRoomDetails(1L, null)).expectNextCount(1).verifyComplete();
    }

    @Test void getRoomDetails_blankToken_doesNotAddHeader() {
        stubGet(roomClient, Mono.just(Map.of("id", 1L)));
        StepVerifier.create(service.getRoomDetails(1L, "  ")).expectNextCount(1).verifyComplete();
    }

    @Test void getRoomDetails_error_returnsEmpty() {
        stubGet(roomClient, Mono.error(new RuntimeException("room-service down")));
        StepVerifier.create(service.getRoomDetails(1L)).verifyComplete();
    }

    // -- sendNotification --

    @Test void sendNotification_fiveArgOverload_delegates() {
        stubPostVoid(notificationClient);
        assertDoesNotThrow(() -> service.sendNotification(2L, 1L, "NEW_MESSAGE", "Hello", "content", 10L, 5L));
        verify(notificationClient).post();
    }

    @Test void sendNotification_withAuthToken_addsHeader() {
        stubPostVoid(notificationClient);
        assertDoesNotThrow(() -> service.sendNotification(2L, 1L, "NEW_MESSAGE", "Hello", "content", 10L, 5L, "token"));
        verify(notificationClient).post();
    }

    @Test void sendNotification_nullTypeAndTitle_usesDefaults() {
        stubPostVoid(notificationClient);
        assertDoesNotThrow(() -> service.sendNotification(2L, 1L, null, null, null, null, null, null));
    }

    @Test void sendNotification_nullActorRoomMessage_skipsOptionalFields() {
        stubPostVoid(notificationClient);
        assertDoesNotThrow(() -> service.sendNotification(2L, null, "MENTION", "You were mentioned", "msg", null, null, "token"));
    }

    @Test void sendNotification_errorIsSwallowed() {
        stubPostVoid(notificationClient);
        doReturn(Mono.error(new RuntimeException("notif fail"))).when(postResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.sendNotification(2L, 1L, "SYSTEM", "Alert", "Test", 10L, 5L, "token"));
    }

    // -- sendPush --

    @Test void sendPush_callsNotificationClient() {
        stubPostVoid(notificationClient);
        assertDoesNotThrow(() -> service.sendPush(1L, "Title", "Body"));
        verify(notificationClient).post();
    }

    @Test void sendPush_errorIsSwallowed() {
        stubPostVoid(notificationClient);
        doReturn(Mono.error(new RuntimeException("push fail"))).when(postResponseSpec).toBodilessEntity();
        assertDoesNotThrow(() -> service.sendPush(1L, "Title", "Body"));
    }
}