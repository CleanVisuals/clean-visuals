/*
 * Copyright (c) 2026, Clean Visuals
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package cleanvisuals.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Shared styling for the panel's controls.
 * <p>
 * Stock Swing buttons render with their own chrome, which reads as a foreign dialog dropped into
 * the sidebar. Everything here paints flat in the client's own palette instead.
 */
public final class PanelComponents
{
	private static final int BUTTON_HEIGHT = 24;

	private PanelComponents()
	{
	}

	public static JButton flatButton(String text, Runnable onClick)
	{
		JButton button = new JButton(text);
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setForeground(ColorScheme.TEXT_COLOR);
		// Lighter than the panel behind it, with a border: at DARK_GRAY on a DARKER_GRAY panel
		// these read as plain text rather than as something you can press.
		button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
		button.setFocusPainted(false);
		button.setOpaque(true);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BORDER_COLOR),
			BorderFactory.createEmptyBorder(3, 6, 3, 6)));
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));
		button.addActionListener(e -> onClick.run());

		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}
		});

		return button;
	}

	/**
	 * A button that asks before it acts. The first click turns it into a question, a second click
	 * within a few seconds goes through with it, and walking away forgets it was ever asked.
	 * <p>
	 * Used instead of a modal for the destructive actions: a dialog you dismiss by reflex is not
	 * really a confirmation, and the button already sits next to the thing it affects.
	 */
	public static JButton confirmButton(String text, String confirmText, Runnable onConfirm)
	{
		ConfirmAction action = new ConfirmAction(text, confirmText, onConfirm);
		JButton button = flatButton(text, action);
		action.button = button;
		return button;
	}

	/**
	 * The two-click state for {@link #confirmButton}. A class rather than a lambda because it has
	 * to remember whether it is armed and hold the timer that disarms it.
	 */
	private static final class ConfirmAction implements Runnable
	{
		private static final int TIMEOUT_MS = 2500;

		private final String text;
		private final String confirmText;
		private final Runnable onConfirm;

		private JButton button;
		private boolean armed;
		private Timer timeout;

		private ConfirmAction(String text, String confirmText, Runnable onConfirm)
		{
			this.text = text;
			this.confirmText = confirmText;
			this.onConfirm = onConfirm;
		}

		@Override
		public void run()
		{
			if (!armed)
			{
				armed = true;
				button.setText(confirmText);

				if (timeout != null)
				{
					timeout.stop();
				}
				timeout = new Timer(TIMEOUT_MS, e -> disarm());
				timeout.setRepeats(false);
				timeout.start();
				return;
			}

			disarm();
			onConfirm.run();
		}

		private void disarm()
		{
			armed = false;
			button.setText(text);

			if (timeout != null)
			{
				timeout.stop();
				timeout = null;
			}
		}
	}

	/**
	 * A row of equally sized buttons.
	 */
	public static JPanel buttonRow(JComponent... buttons)
	{
		JPanel row = new JPanel(new GridLayout(1, buttons.length, 4, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, BUTTON_HEIGHT + 4));
		for (JComponent button : buttons)
		{
			row.add(button);
		}
		return row;
	}

	public static JCheckBox checkBox(String text, String tooltip)
	{
		JCheckBox box = new JCheckBox(text);
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setFocusable(false);
		box.setToolTipText(tooltip);
		box.setCursor(new Cursor(Cursor.HAND_CURSOR));
		box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		return box;
	}

	public static void style(JComboBox<?> combo)
	{
		combo.setBackground(ColorScheme.DARK_GRAY_COLOR);
		combo.setForeground(ColorScheme.TEXT_COLOR);
		combo.setFont(FontManager.getRunescapeSmallFont());
		combo.setFocusable(false);
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
	}

	public static JLabel title(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		return label;
	}

	public static JLabel hint(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}
}
