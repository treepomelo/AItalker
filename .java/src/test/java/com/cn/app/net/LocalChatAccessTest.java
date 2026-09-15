package com.cn.app.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalChatAccessTest {
    @Test
    void localProfileAcceptsAnonymousChatToken() {
        assertTrue(TextChatSocket.isLocalAnonymousToken("local", true));
    }

    @Test
    void nonLocalProfileRejectsAnonymousChatToken() {
        assertFalse(TextChatSocket.isLocalAnonymousToken("local", false));
        assertFalse(TextChatSocket.isLocalAnonymousToken("", true));
    }
}