package com.connecthub.websocket.payload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("STOMP Payload DTO Tests")
class StompPayloadsTest {

    private static final List<Class<?>> PAYLOAD_TYPES = Arrays.asList(
            StompPayloads.ChatMessagePayload.class,
            StompPayloads.TypingPayload.class,
            StompPayloads.ReadReceiptPayload.class,
            StompPayloads.ReactionPayload.class,
            StompPayloads.PresenceUpdatePayload.class,
            StompPayloads.EditPayload.class,
            StompPayloads.DeletePayload.class,
            StompPayloads.StatusUpdatePayload.class,
            StompPayloads.ChatMessageEvent.class,
            StompPayloads.TypingIndicatorEvent.class,
            StompPayloads.ReadReceiptEvent.class,
            StompPayloads.MessageEditEvent.class,
            StompPayloads.MessageDeleteEvent.class,
            StompPayloads.ReactionEvent.class,
            StompPayloads.PresenceUpdateEvent.class,
            StompPayloads.DeliveryStatusEvent.class,
            StompPayloads.RoomJoinEvent.class,
            StompPayloads.RoomLeaveEvent.class,
            StompPayloads.PersonalAlertEvent.class
    );

    @Test
    void outerPayloadContainer_canBeConstructed() {
        assertThatNoException().isThrownBy(StompPayloads::new);
    }

    @Test
    void payloads_supportNoArgsAccessorsEqualsHashCodeAndToString() {
        for (Class<?> payloadType : PAYLOAD_TYPES) {
            Object first = newInstance(payloadType);
            Object second = newInstance(payloadType);

            for (Field field : payloadFields(payloadType)) {
                Object value = valueFor(field, 1);
                callSetterIfPresent(first, field, value);
                callSetterIfPresent(second, field, value);
                assertGetterIfPresent(first, field, value);
            }

            assertThat(first)
                    .as(payloadType.getSimpleName() + " equality")
                    .isEqualTo(second)
                    .hasSameHashCodeAs(second)
                    .isNotEqualTo(null)
                    .isNotEqualTo("different-type");
            assertThat(first.toString()).contains(payloadType.getSimpleName());
        }
    }

    @Test
    void payloads_detectUnequalFields() {
        for (Class<?> payloadType : PAYLOAD_TYPES) {
            Object first = newInstance(payloadType);
            Object second = newInstance(payloadType);

            Field mutableField = null;
            for (Field field : payloadFields(payloadType)) {
                callSetterIfPresent(first, field, valueFor(field, 1));
                callSetterIfPresent(second, field, valueFor(field, 1));
                if (mutableField == null && hasSetter(payloadType, field)) {
                    mutableField = field;
                }
            }

            if (mutableField != null) {
                callSetterIfPresent(second, mutableField, valueFor(mutableField, 2));
                assertThat(first)
                        .as(payloadType.getSimpleName() + " detects changed " + mutableField.getName())
                        .isNotEqualTo(second);
            }
        }
    }

    @Test
    void payloads_supportAllArgsConstructors() {
        for (Class<?> payloadType : PAYLOAD_TYPES) {
            Constructor<?> constructor = Arrays.stream(payloadType.getDeclaredConstructors())
                    .max(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow();
            Object[] args = Arrays.stream(constructor.getParameterTypes())
                    .map(type -> valueFor(type, 3))
                    .toArray();

            Object instance = invokeConstructor(constructor, args);

            assertThat(instance).isInstanceOf(payloadType);
            assertThat(instance.toString()).contains(payloadType.getSimpleName());
        }
    }

    @Test
    void payloads_supportBuildersAndBuilderToString() {
        for (Class<?> payloadType : PAYLOAD_TYPES) {
            Object builder = invokeStatic(payloadType, "builder");

            for (Field field : payloadFields(payloadType)) {
                Method builderSetter = findMethod(builder.getClass(), field.getName(), field.getType());
                if (builderSetter != null) {
                    invoke(builderSetter, builder, valueFor(field, 4));
                }
            }

            assertThat(builder.toString()).contains(payloadType.getSimpleName());
            Object built = invoke(findMethod(builder.getClass(), "build"), builder);

            assertThat(built).isInstanceOf(payloadType);
            assertThat(built.toString()).contains(payloadType.getSimpleName());
        }
    }

    @Test
    void outboundPayloadBuilders_applyDefaultTypeDiscriminators() {
        assertThat(StompPayloads.ChatMessageEvent.builder().build().getType()).isEqualTo("CHAT_MESSAGE");
        assertThat(StompPayloads.TypingIndicatorEvent.builder().build().getType()).isEqualTo("TYPING_INDICATOR");
        assertThat(StompPayloads.ReadReceiptEvent.builder().build().getType()).isEqualTo("READ_RECEIPT");
        assertThat(StompPayloads.MessageEditEvent.builder().build().getType()).isEqualTo("MESSAGE_EDIT");
        assertThat(StompPayloads.MessageDeleteEvent.builder().build().getType()).isEqualTo("MESSAGE_DELETE");
        assertThat(StompPayloads.ReactionEvent.builder().build().getType()).isEqualTo("REACTION");
        assertThat(StompPayloads.PresenceUpdateEvent.builder().build().getType()).isEqualTo("PRESENCE_UPDATE");
        assertThat(StompPayloads.DeliveryStatusEvent.builder().build().getType()).isEqualTo("MESSAGE_STATUS");
        assertThat(StompPayloads.RoomJoinEvent.builder().build().getType()).isEqualTo("ROOM_JOIN");
        assertThat(StompPayloads.RoomLeaveEvent.builder().build().getType()).isEqualTo("ROOM_LEAVE");
        assertThat(StompPayloads.PersonalAlertEvent.builder().build().getType()).isEqualTo("PERSONAL_ALERT");
    }

    private static List<Field> payloadFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
    }

    private static Object newInstance(Class<?> type) {
        Constructor<?> constructor = Arrays.stream(type.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 0)
                .findFirst()
                .orElseThrow();
        return invokeConstructor(constructor);
    }

    private static Object invokeConstructor(Constructor<?> constructor, Object... args) {
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not construct " + constructor.getDeclaringClass().getSimpleName(), e);
        }
    }

    private static Object invokeStatic(Class<?> type, String methodName) {
        return invoke(findMethod(type, methodName), null);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Could not invoke " + method.getName(), e);
        }
    }

    private static void callSetterIfPresent(Object target, Field field, Object value) {
        Method setter = findMethod(target.getClass(), setterName(field), field.getType());
        if (setter != null) {
            invoke(setter, target, value);
        }
    }

    private static boolean hasSetter(Class<?> type, Field field) {
        return findMethod(type, setterName(field), field.getType()) != null;
    }

    private static void assertGetterIfPresent(Object target, Field field, Object expected) {
        Method getter = findMethod(target.getClass(), getterName(field));
        if (getter == null && field.getType().equals(boolean.class)) {
            getter = findMethod(target.getClass(), "is" + capitalize(field.getName()));
        }
        if (getter != null && hasSetter(target.getClass(), field)) {
            assertThat(invoke(getter, target)).isEqualTo(expected);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static String setterName(Field field) {
        return "set" + capitalize(field.getName());
    }

    private static String getterName(Field field) {
        return "get" + capitalize(field.getName());
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static Object valueFor(Field field, int variant) {
        if ("type".equals(field.getName()) && field.getType().equals(String.class)) {
            return "CUSTOM_TYPE_" + variant;
        }
        return valueFor(field.getType(), variant);
    }

    private static Object valueFor(Class<?> type, int variant) {
        if (type.equals(Long.class)) {
            return 100L + variant;
        }
        if (type.equals(String.class)) {
            return "value-" + variant;
        }
        if (type.equals(Boolean.class)) {
            return variant % 2 == 0;
        }
        if (type.equals(boolean.class)) {
            return variant % 2 == 0;
        }
        if (type.equals(LocalDateTime.class)) {
            return LocalDateTime.parse("2026-05-08T10:15:30").plusDays(variant);
        }
        throw new AssertionError("Unhandled payload field type: " + type.getName());
    }
}
