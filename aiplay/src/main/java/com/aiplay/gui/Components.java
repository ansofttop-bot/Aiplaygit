package com.aiplay.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Small immediate-mode widget set for the ClickGUI (dark theme). */
public final class Components {
	public static final int BG = 0xF00D0F15, SIDE = 0xFF12141B, CARD = 0xFF1A1D27, CARD_HOVER = 0xFF232736,
			ACCENT = 0xFF7C6CFF, TEXT = 0xFFE8EAF2, DIM = 0xFF8B92A8, GOOD = 0xFF3DDC84, BAD = 0xFFFF5470,
			WARN = 0xFFFFB84D, OFF = 0xFF2C3040;

	private Components() {}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = 0;
		for (int s = 24; s >= 0; s -= 8) r |= ((int) (((a >> s) & 255) * (1 - t) + ((b >> s) & 255) * t) & 255) << s;
		return r;
	}

	public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
		r = Math.min(r, Math.min(w, h) / 2);
		for (int i = 0; i < h; i++) {
			int d = i < r ? r - i : (i >= h - r ? i - (h - r) + 1 : 0);
			int inset = d == 0 ? 0 : (int) Math.round(r - Math.sqrt(Math.max(0, r * r - (d - 0.5) * (d - 0.5))));
			c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
		}
	}

	public static abstract class Row {
		public int x, y, w, h = 27;
		public void layout(int x, int y, int w) { this.x = x; this.y = y; this.w = w; }
		public boolean hit(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h - 3; }
		public abstract void render(DrawContext c, TextRenderer tr, int mx, int my);
		public boolean click(int mx, int my, int btn) { return false; }
		public void drag(int mx) {}
	}

	public static class Toggle extends Row {
		final String label; final BooleanSupplier get; final Consumer<Boolean> set; float anim = -1;
		public Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) { this.label = label; this.get = get; this.set = set; }
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			boolean on = get.getAsBoolean();
			if (anim < 0) anim = on ? 1 : 0;
			anim += ((on ? 1 : 0) - anim) * 0.3f;
			rrect(c, x, y, w, h - 3, 4, hit(mx, my) ? CARD_HOVER : CARD);
			c.drawText(tr, label, x + 8, y + (h - 3 - 8) / 2, TEXT, false);
			int px = x + w - 34, py = y + (h - 3 - 12) / 2;
			rrect(c, px, py, 26, 12, 6, mix(OFF, ACCENT, anim));
			rrect(c, px + 2 + Math.round(anim * 14), py + 2, 8, 8, 4, 0xFFFFFFFF);
		}
		@Override public boolean click(int mx, int my, int btn) {
			if (btn == 0 && hit(mx, my)) { set.accept(!get.getAsBoolean()); return true; }
			return false;
		}
	}

	public static class Slider extends Row {
		final String label, unit; final double min, max, step; final DoubleSupplier get; final DoubleConsumer set;
		public Slider(String label, double min, double max, double step, String unit, DoubleSupplier get, DoubleConsumer set) {
			this.label = label; this.min = min; this.max = max; this.step = step; this.unit = unit; this.get = get; this.set = set; h = 33;
		}
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			rrect(c, x, y, w, h - 3, 4, CARD);
			double v = get.getAsDouble();
			String val = (step < 1 ? String.format("%.2f", v) : String.valueOf((int) Math.round(v))) + unit;
			c.drawText(tr, label, x + 8, y + 5, TEXT, false);
			c.drawText(tr, val, x + w - 8 - tr.getWidth(val), y + 5, ACCENT, false);
			int tx = x + 8, tw = w - 16, ty = y + 20;
			rrect(c, tx, ty, tw, 4, 2, OFF);
			float t = (float) ((v - min) / (max - min));
			rrect(c, tx, ty, Math.max(4, Math.round(tw * t)), 4, 2, ACCENT);
			rrect(c, tx + Math.round(tw * t) - 3, ty - 2, 7, 8, 3, 0xFFFFFFFF);
		}
		@Override public boolean click(int mx, int my, int btn) {
			if (btn == 0 && hit(mx, my)) { drag(mx); return true; }
			return false;
		}
		@Override public void drag(int mx) {
			double t = Math.max(0, Math.min(1, (mx - (x + 8)) / (double) (w - 16)));
			double v = min + t * (max - min);
			v = Math.round(v / step) * step;
			set.accept(Math.max(min, Math.min(max, v)));
		}
	}

	public static class Button extends Row {
		final String label; final Runnable action; final int color;
		public Button(String label, int color, Runnable action) { this.label = label; this.color = color; this.action = action; }
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			rrect(c, x, y, w, h - 3, 4, hit(mx, my) ? mix(CARD, color, 0.55f) : mix(CARD, color, 0.3f));
			c.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 3 - 8) / 2, TEXT, false);
		}
		@Override public boolean click(int mx, int my, int btn) {
			if (btn == 0 && hit(mx, my)) { action.run(); return true; }
			return false;
		}
	}

	public static class Header extends Row {
		final String text;
		public Header(String text) { this.text = text; h = 20; }
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			c.fill(x, y + 4, x + 2, y + 13, ACCENT);
			c.drawText(tr, text.toUpperCase(), x + 7, y + 5, DIM, false);
		}
	}

	public static class Label extends Row {
		final String text; final OrderedText ot; final int color;
		public Label(String text, int color) { this.text = text; this.ot = null; this.color = color; h = 12; }
		public Label(OrderedText ot, int color) { this.text = null; this.ot = ot; this.color = color; h = 11; }
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			if (ot != null) c.drawText(tr, ot, x + 2, y + 1, color, false);
			else c.drawText(tr, tr.trimToWidth(text, w - 4), x + 2, y + 1, color, false);
		}
	}

	/** name + status + optional button (Items tab, memory list). */
	public static class ItemRow extends Row {
		final String name, status, btn; final int statusColor; final Runnable action;
		public ItemRow(String name, String status, int statusColor, String btn, Runnable action) {
			this.name = name; this.status = status; this.statusColor = statusColor; this.btn = btn; this.action = action;
		}
		int bw(TextRenderer tr) { return btn == null ? 0 : tr.getWidth(btn) + 12; }
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			rrect(c, x, y, w, h - 3, 4, CARD);
			int bw = bw(tr);
			int sw = status == null ? 0 : tr.getWidth(status) + 10;
			c.drawText(tr, tr.trimToWidth(name, w - bw - sw - 22), x + 8, y + (h - 3 - 8) / 2, TEXT, false);
			if (status != null) c.drawText(tr, status, x + w - bw - sw - 4 + 10 - 4, y + (h - 3 - 8) / 2, statusColor, false);
			if (btn != null) {
				int bx = x + w - bw - 4;
				boolean hv = mx >= bx && mx < bx + bw && my >= y + 3 && my < y + h - 6;
				rrect(c, bx, y + 3, bw, h - 9, 3, hv ? ACCENT : mix(CARD, ACCENT, 0.4f));
				c.drawText(tr, btn, bx + 6, y + (h - 3 - 8) / 2, TEXT, false);
			}
		}
		@Override public boolean click(int mx, int my, int btnId) {
			if (btnId != 0 || btn == null || action == null) return false;
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			int bw = bw(mc.textRenderer), bx = x + w - bw - 4;
			if (mx >= bx && mx < bx + bw && my >= y + 3 && my < y + h - 6) { action.run(); return true; }
			return false;
		}
	}

	/** Label + vanilla text field (the field itself is positioned/drawn by the screen). */
	public static class FieldRow extends Row {
		final String label; public final net.minecraft.client.gui.widget.TextFieldWidget field;
		public FieldRow(String label, net.minecraft.client.gui.widget.TextFieldWidget f) { this.label = label; this.field = f; h = 38; }
		@Override public void render(DrawContext c, TextRenderer tr, int mx, int my) {
			c.drawText(tr, label, x + 2, y + 1, DIM, false);
			field.setX(x); field.setY(y + 12); field.setWidth(w); field.visible = true;
		}
	}
}
