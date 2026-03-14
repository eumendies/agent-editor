package com.agent.editor.agent.v2.event;

public class WebSocketEventPublisher implements EventPublisher {

    @Override
    public void publish(ExecutionEvent event) {
        // v2 will bridge events to websocket transport in a later vertical slice.
    }
}
