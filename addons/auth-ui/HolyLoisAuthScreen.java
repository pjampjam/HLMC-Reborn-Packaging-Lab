package holylois.auth;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** Full canvas backdrop in the launcher look (AuthUi). Passwords live only in input widgets until sent to EasyAuth. */
public final class HolyLoisAuthScreen extends Screen {
    private AuthStatus state;
    private EditBox password, confirm;
    private Button submit;
    private String notice = "";
    private boolean quitting, waiting;
    private int submittedTicks;
    public HolyLoisAuthScreen(AuthStatus state) { super(Component.literal("Holy Lois: Reborn")); this.state = state; }
    public void update(AuthStatus next) {
        if (state.mode() != next.mode()) { clearPasswords(); minecraft.gui.setScreen(new HolyLoisAuthScreen(next)); }
        else state = next;
    }
    public void feedback(String message) {
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("wrong password") || lower.contains("incorrect password")) notice = "Wrong password. Try again.";
        else if (lower.contains("match")) notice = "The passwords do not match.";
        else if (lower.contains("characters") && !lower.contains("/register")) notice = "Use at least " + state.minimumLength() + " characters.";
        else return;
        waiting = false; if (submit != null) submit.active = true;
    }
    private int top() { return Math.max(8, (height - 222) / 2); }
    @Override protected void init() {
        int x = width / 2 - 118, y = top();
        if (quitting) {
            addRenderableWidget(new ActionButton(x, y + 100, 236, "Back", AuthUi.Kind.NORMAL, b -> minecraft.gui.setScreen(new HolyLoisAuthScreen(state))));
            addRenderableWidget(new ActionButton(x, y + 130, 236, "Disconnect", AuthUi.Kind.DANGER, b -> {
                clearPasswords(); HolyLoisAuthClient.status = null;
                minecraft.disconnectFromWorld(Component.literal("Disconnected from Holy Lois: Reborn"));
            }));
        } else if (state.mode() != 3) {
            password = addRenderableWidget(new PasswordBox(x, y + 86, "Password"));
            if (state.mode() == 2) confirm = addRenderableWidget(new PasswordBox(x, y + 130, "Confirm password"));
            submit = addRenderableWidget(new ActionButton(x, y + (state.mode() == 2 ? 162 : 124), 236,
                state.mode() == 2 ? "Create account" : "Log in", AuthUi.Kind.CONFIRM, b -> submit()));
            setInitialFocus(password);
        }
    }
    /** A field drawn like the launcher's text box: canvas fill, line border, gold when focused. */
    private final class PasswordBox extends EditBox {
        private final String label;
        PasswordBox(int x, int y, String label) {
            super(font, x + 8, y + 8, 220, 10, Component.literal(label)); this.label = label;
            setMaxLength(100); setTextColor(AuthUi.TEXT); setTextShadow(false); setBordered(false);
            addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
            AuthUi.box(g, getX() - 8, getY() - 8, 236, 24, AuthUi.CANVAS, isFocused() ? AuthUi.GOLD : isHovered() ? AuthUi.LINE_STRONG : AuthUi.LINE);
            super.extractWidgetRenderState(g, mx, my, delta);
        }
        @Override public boolean keyPressed(KeyEvent event) {
            if (enter(event.key())) { lastEnterPress = System.currentTimeMillis(); submit(); return true; }
            return super.keyPressed(event);
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, label + ", " + getValue().length() + " characters");
        }
    }
    private final class ActionButton extends Button {
        private final AuthUi.Kind kind;
        ActionButton(int x, int y, int width, String text, AuthUi.Kind kind, OnPress action) {
            super(x, y, width, 24, Component.literal(text), action, DEFAULT_NARRATION); this.kind = kind;
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
            AuthUi.button(graphics, font, getX(), getY(), getWidth(), getHeight(), getMessage(), isHovered(), isFocused(), active, kind);
        }
    }
    private void submit() {
        if (waiting || password == null || minecraft.getConnection() == null) return;
        boolean registration = state.mode() == 2;
        String value = password.getValue();
        notice = AuthPolicy.validate(value, confirm == null ? "" : confirm.getValue(), registration, state.minimumLength());
        if (!notice.isEmpty()) return;
        String command = registration ? "register " + AuthPolicy.argument(value) + " " + AuthPolicy.argument(confirm.getValue())
            : "login " + AuthPolicy.argument(value);
        minecraft.getConnection().sendCommand(command);
        clearPasswords(); waiting = true; submittedTicks = 0; submit.active = false;
        notice = "Checking with the server...";
    }
    private void clearPasswords() { if (password != null) password.setValue(""); if (confirm != null) confirm.setValue(""); }
    @Override public void removed() { clearPasswords(); }
    @Override public void tick() {
        if (waiting && ++submittedTicks >= 60) {
            waiting = false; if (submit != null) submit.active = true;
            notice = "Not logged in yet. Check your password and try again.";
        }
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            clearPasswords();
            var next = new HolyLoisAuthScreen(state); next.quitting = !quitting;
            minecraft.gui.setScreen(next); return true;
        }
        if (enter(event.key())) { lastEnterPress = System.currentTimeMillis(); submit(); return true; }
        return super.keyPressed(event);
    }
    // With 26.3 text input (IME) on, Windows text services can swallow Enter in a focused box: it then arrives only as a
    // typed line break or as the key release. Every path submits once; `waiting` blocks a second send.
    // Shared by every auth screen: when the server swaps the screen between press and release, the release must not
    // submit the new, empty form.
    private static long lastEnterPress;
    private static boolean pressSeen() { return System.currentTimeMillis() - lastEnterPress < 1500; }
    private boolean enter(int key) { return !quitting && state.mode() != 3 && (key == 257 || key == 335); }
    @Override public boolean keyReleased(KeyEvent event) {
        if (enter(event.key())) { if (!pressSeen()) submit(); return true; }
        return super.keyReleased(event);
    }
    @Override public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (enter(257) && (event.codepoint() == '\r' || event.codepoint() == '\n')) { if (!pressSeen()) submit(); return true; }
        return super.charTyped(event);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { g.fill(0, 0, width, height, AuthUi.CANVAS); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        if (state.mode() == 3 && !quitting) {
            g.centeredText(font, "HOLY LOIS: REBORN", width/2, height/2 - 16, AuthUi.GOLD);
            g.centeredText(font, "Arriving...", width/2, height/2, AuthUi.MUTED);
            return;
        }
        int left = width/2 - 140, top = top();
        AuthUi.box(g, left, top, 280, 222, AuthUi.SURFACE, AuthUi.LINE);
        g.nextStratum();
        g.centeredText(font, "HOLY LOIS: REBORN", width/2, top + 14, AuthUi.GOLD);
        g.centeredText(font, quitting ? "Leave the server?" : state.mode() == 2 ? "Create your server account" : "Welcome back", width/2, top + 32, AuthUi.TEXT);
        g.fill(width/2 - 12, top + 44, width/2 + 12, top + 45, AuthUi.GOLD);
        if (!quitting && state.mode() != 3) {
            g.centeredText(font, state.mode() == 2 ? "At least " + state.minimumLength() + " characters, no spaces." : "Use the password you registered here.", width/2, top + 54, AuthUi.MUTED);
            g.text(font, "Password", width/2 - 118, top + 73, AuthUi.MUTED, false);
            if (state.mode() == 2) g.text(font, "Confirm password", width/2 - 118, top + 117, AuthUi.MUTED, false);
        }
        if (!notice.isEmpty() && !quitting) g.centeredText(font, notice, width/2, top + 196, waiting ? AuthUi.MUTED : AuthUi.DANGER);
        g.centeredText(font, "Esc: leave server", width/2, Math.min(height - 10, top + 230), AuthUi.alpha(AuthUi.MUTED, 0.6f));
        g.nextStratum();
        super.extractRenderState(g, x, y, delta);
    }
}
