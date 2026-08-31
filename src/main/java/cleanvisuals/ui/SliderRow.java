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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One labelled setting, adjustable three ways: drag the slider, nudge the spinner by one, or
 * type an exact number.
 * <p>
 * The slider alone could not reach every value. In a sidebar this narrow a 10-400 zoom range
 * works out at several units per pixel, so dragging skips numbers -- fine for finding a look by
 * eye, useless when you want exactly 112. The spinner is the same value from the other side:
 * exact, typeable, and stepping one at a time. The slider is also focusable and takes the mouse
 * wheel, so arrow keys and a scroll both move it by one.
 * <p>
 * The overlay re-reads config every frame, so writing on every tick of a drag is what makes the
 * game update as the slider moves rather than when it is released.
 */
public class SliderRow extends JPanel
{
	private static final int LABEL_WIDTH = 54;
	private static final int SPINNER_WIDTH = 62;
	private static final int ROW_HEIGHT = 22;
	private static final int SPINNER_COLUMNS = 4;

	private final JSlider slider;
	private final JSpinner spinner;
	private final IntConsumer onChange;

	/**
	 * True while the two controls are being brought into step with each other, or filled in from
	 * config. Both of those move a control without the user having asked for anything, so neither
	 * should be reported as a change or echoed back out as a config write.
	 */
	private boolean updating;

	public SliderRow(String label, int min, int max, String tooltip, IntConsumer onChange)
	{
		this.onChange = onChange;
		this.slider = new JSlider(min, max, min);
		this.spinner = new JSpinner(new SpinnerNumberModel(min, min, max, 1));

		setLayout(new BorderLayout(4, 0));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		setToolTipText(tooltip);

		JLabel name = new JLabel(label);
		name.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setPreferredSize(new Dimension(LABEL_WIDTH, ROW_HEIGHT));
		name.setToolTipText(tooltip);

		slider.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		slider.setToolTipText(tooltip);
		// Focusable so the arrow keys reach it, and wheel-scrollable, since both move by exactly
		// one where a drag cannot.
		slider.setFocusable(true);
		slider.addChangeListener(e -> userSet(slider.getValue()));
		slider.addMouseWheelListener(e -> userSet(slider.getValue() - e.getWheelRotation()));

		JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		field.setColumns(SPINNER_COLUMNS);
		field.setFont(FontManager.getRunescapeSmallFont());
		spinner.setToolTipText(tooltip);
		spinner.setPreferredSize(new Dimension(SPINNER_WIDTH, ROW_HEIGHT));
		spinner.addChangeListener(e -> userSet((Integer) spinner.getValue()));

		add(name, BorderLayout.WEST);
		add(slider, BorderLayout.CENTER);
		add(spinner, BorderLayout.EAST);
	}

	/**
	 * A change the user made, from whichever control they used. Brings the other one into step
	 * and reports it once.
	 */
	private void userSet(int value)
	{
		if (updating)
		{
			return;
		}

		int clamped = clamp(value);

		updating = true;
		try
		{
			slider.setValue(clamped);
			spinner.setValue(clamped);
		}
		finally
		{
			updating = false;
		}

		onChange.accept(clamped);
	}

	/**
	 * Sets the displayed value without reporting it as a change.
	 */
	public void setValue(int newValue)
	{
		int clamped = clamp(newValue);

		updating = true;
		try
		{
			slider.setValue(clamped);
			spinner.setValue(clamped);
		}
		finally
		{
			updating = false;
		}
	}

	private int clamp(int value)
	{
		return Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), value));
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, ROW_HEIGHT);
	}
}
