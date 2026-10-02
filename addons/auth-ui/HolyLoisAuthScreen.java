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

/** Full black backdrop. Passwords live only in input widgets until sent to EasyAuth. */
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
    private int top() { return Math.max(8, (height - 218) / 2); }
    @Override protected void init() {
        int x = width / 2 - 118, y = top();
        if (quitting) {
            addRenderableWidget(new ActionButton(x, y + 100, 236, "Back", b -> minecraft.gui.setScreen(new HolyLoisAuthScreen(state))));
            addRenderableWidget(new ActionButton(x, y + 130, 236, "Disconnect", b -> {
                clearPasswords(); HolyLoisAuthClient.status = null;
                minecraft.disconnectFromWorld(Component.literal("Disconnected from Holy Lois: Reborn"));
            }));
        } else if (state.mode() != 3) {
            password = addRenderableWidget(new PasswordBox(x, y + 83, "Password"));
            if (state.mode() == 2) confirm = addRenderableWidget(new PasswordBox(x, y + 126, "Confirm password"));
            submit = addRenderableWidget(new ActionButton(x, y + (state.mode() == 2 ? 158 : 123), 236,
                state.mode() == 2 ? "Create account" : "Log in", b -> submit()));
            setInitialFocus(password);
        }
    }
    private final class PasswordBox extends EditBox {
        private final String label;
        PasswordBox(int x, int y, String label) {
            super(font, x, y, 236, 22, Component.literal(label)); this.label = label;
            setMaxLength(100); setTextColor(0xFFECECEC); setTextShadow(false);
            addFormatter((text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
        }
        @Override public void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, label + ", " + getValue().length() + " characters");
        }
    }
    private final class ActionButton extends Button {
        ActionButton(int x, int y, int width, String text, OnPress action) {
            super(x, y, width, 24, Component.literal(text), action, DEFAULT_NARRATION);
        }
        @Override protected void extractContents(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHoveredOrFocused() ? 0xFF272727 : 0xFF161616);
            graphics.outline(getX(), getY(), getWidth(), getHeight(), active ? 0xFFC9B450 : 0xFF454545);
            graphics.centeredText(font, getMessage(), getX() + getWidth()/2, getY() + 8, active ? 0xFFE3CF6A : 0xFF777777);
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
        if (!quitting && state.mode() != 3 && (event.key() == 257 || event.key() == 335)) { submit(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean isInGameUi() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) { g.fill(0, 0, width, height, 0xFF000000); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        if (state.mode() == 3 && !quitting) {
            g.centeredText(font, "Arriving...", width/2, height/2 - 4, 0xFFECECEC);
            return;
        }
        int left = width/2 - 150, top = top();
        g.fill(left, top, left + 300, top + 218, 0xFF0E0E0E);
        g.fill(left, top, left + 300, top + 2, 0xFFC9B450);
        g.nextStratum();
        g.centeredText(font, "HOLY LOIS: REBORN", width/2, top + 16, 0xFFE3CF6A);
        g.centeredText(font, quitting ? "Leave the server?" : state.mode() == 2 ? "Create your server account" : state.mode() == 3 ? "Finding a safe place..." : "Welcome back", width/2, top + 38, 0xFFECECEC);
        if (!quitting && state.mode() != 3) {
            g.text(font, "Password", width/2 - 118, top + 70, 0xFFAAAAAA);
            if (state.mode() == 2) g.text(font, "Confirm password", width/2 - 118, top + 113, 0xFFAAAAAA);
            g.centeredText(font, state.mode() == 2 ? "At least " + state.minimumLength() + " characters, no spaces." : "Use the password you registered here.", width/2, top + 56, 0xFF999999);
        }
        if (!notice.isEmpty() && !quitting) g.centeredText(font, notice, width/2, top + 191, 0xFFDDC265);
        g.centeredText(font, "Esc: leave server", width/2, Math.min(height - 10, top + 225), 0xFF777777);
        g.nextStratum();
        super.extractRenderState(g, x, y, delta);
    }
}
