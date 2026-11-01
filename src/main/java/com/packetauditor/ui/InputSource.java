package com.packetauditor.ui;

/**
 * Optional polling view of input state (D5/D12), complementing the event-driven
 * {@link InputEvent} dispatch. Components primarily react to {@link InputEvent}s;
 * an {@code InputSource} lets a renderer binding expose live pointer/key state
 * for components that prefer polling. The MC reference binding implements this
 * from Minecraft's mouse/keyboard; Codex can implement it from your input system.
 */
public interface InputSource {

    double mouseX();

    double mouseY();

    boolean isMouseButtonDown(int button);

    boolean isKeyDown(int key);
}
