package dev.cbui.chat;

/** Implemented by the chat mixin so the chat screen mixin can ask which message is under the mouse. */
public interface ChatAccess {
  /** Full text of the chat message at the (raw LWJGL) mouse position, or null when the mouse is not over a line. */
  String cbui$messageAt(int rawMouseX, int rawMouseY);
}
