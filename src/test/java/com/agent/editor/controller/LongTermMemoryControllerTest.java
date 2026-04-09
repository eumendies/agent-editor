package com.agent.editor.controller;

import com.agent.editor.dto.UserProfileMemoryRequest;
import com.agent.editor.dto.UserProfileMemoryResponse;
import com.agent.editor.service.TaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LongTermMemoryControllerTest {

    @Test
    void shouldListUserProfiles() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);
        List<UserProfileMemoryResponse> response = List.of(profile("memory-1", "Default to Chinese"));

        when(taskApplicationService.listUserProfiles()).thenReturn(response);

        ResponseEntity<List<UserProfileMemoryResponse>> result = controller.listUserProfiles();

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldCreateUserProfile() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);
        UserProfileMemoryRequest request = new UserProfileMemoryRequest();
        request.setSummary("Default to Chinese");
        UserProfileMemoryResponse response = profile("memory-1", "Default to Chinese");

        when(taskApplicationService.createUserProfile(request)).thenReturn(response);

        ResponseEntity<UserProfileMemoryResponse> result = controller.createUserProfile(request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
    }

    @Test
    void shouldUpdateUserProfile() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);
        UserProfileMemoryRequest request = new UserProfileMemoryRequest();
        request.setSummary("Prefer concise summaries");
        UserProfileMemoryResponse response = profile("memory-1", "Prefer concise summaries");

        when(taskApplicationService.updateUserProfile("memory-1", request)).thenReturn(response);

        ResponseEntity<UserProfileMemoryResponse> result = controller.updateUserProfile("memory-1", request);

        assertEquals(200, result.getStatusCode().value());
        assertSame(response, result.getBody());
        verify(taskApplicationService).updateUserProfile("memory-1", request);
    }

    @Test
    void shouldDeleteUserProfile() {
        TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
        LongTermMemoryController controller = new LongTermMemoryController(taskApplicationService);

        ResponseEntity<Void> result = controller.deleteUserProfile("memory-1");

        assertEquals(200, result.getStatusCode().value());
        assertNull(result.getBody());
        verify(taskApplicationService).deleteUserProfile("memory-1");
    }

    @Test
    void shouldUseFinalMemoryRoutePrefix() {
        RequestMapping mapping = LongTermMemoryController.class.getAnnotation(RequestMapping.class);

        assertEquals("/api/memory", mapping.value()[0]);
    }

    private UserProfileMemoryResponse profile(String memoryId, String summary) {
        UserProfileMemoryResponse response = new UserProfileMemoryResponse();
        response.setMemoryId(memoryId);
        response.setSummary(summary);
        response.setMemoryType("USER_PROFILE");
        return response;
    }
}
