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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * A clickable title bar that shows or hides its content.
 * <p>
 * Three of these stacked make up the backgrounds panel: every region stays visible as a header,
 * but only the one being edited spends vertical space on a preview and a column of sliders.
 * <p>
 * The arrow is drawn rather than loaded: a plugin jar shipped from the hub cannot read the
 * client's own resources, so borrowing its arrow would look right here and break for users.
 */
public class CollapsibleSection extends JPanel
{
	private static final ImageIcon COLLAPSED = new ImageIcon(triangle(false));
	private static final ImageIcon EXPANDED = new ImageIcon(triangle(true));

	/**
	 * The arrow, drawn rather than loaded from a file.
	 * <p>
	 * The client has a perfectly good one at {@code /util/arrow_right.png}, and borrowing it
	 * works when the plugin is side-loaded onto the classpath -- but not once it ships from the
	 * hub. {@code PluginClassLoader} is built with a null parent and falls back to the client
	 * only for {@code loadClass}, never for {@code getResource}, so a plugin jar can read the
	 * client's classes and none of its resources. Borrowing it would mean the panel looking one
	 * way here and another way for everyone who installs it.
	 */
	private static BufferedImage triangle(boolean pointingDown)
	{
		final int size = 8;
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);

		Polygon shape = new Polygon();
		if (pointingDown)
		{
			shape.addPoint(0, 1);
			shape.addPoint(size, 1);
			shape.addPoint(size / 2, size - 1);
		}
		else
		{
			shape.addPoint(1, 0);
			shape.addPoint(size - 1, size / 2);
			shape.addPoint(1, size);
		}

		g.fillPolygon(shape);
		g.dispose();
		return image;
	}

	private final JLabel arrow = new JLabel(COLLAPSED);
	private final JPanel content = new JPanel();

	private boolean expanded;
	private Runnable onExpanded;

	public CollapsibleSection(String title, JComponent headerControl)
	{
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

		JLabel titleLabel = new JLabel(title);
		titleLabel.setForeground(Color.WHITE);
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));

		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 4));
		header.setCursor(new Cursor(Cursor.HAND_CURSOR));
		header.add(arrow, BorderLayout.WEST);
		header.add(titleLabel, BorderLayout.CENTER);
		if (headerControl != null)
		{
			header.add(headerControl, BorderLayout.EAST);
		}

		MouseAdapter toggle = new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setExpanded(!expanded);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				header.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		};

		// The labels sit on top of the header and would otherwise swallow the click meant for it.
		header.addMouseListener(toggle);
		arrow.addMouseListener(toggle);
		titleLabel.addMouseListener(toggle);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		content.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
		content.setVisible(false);

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);
	}

	public JPanel getContent()
	{
		return content;
	}

	public boolean isExpanded()
	{
		return expanded;
	}

	/**
	 * Called when this section opens, so a parent can close its siblings.
	 */
	public void setOnExpanded(Runnable onExpanded)
	{
		this.onExpanded = onExpanded;
	}

	public void setExpanded(boolean expand)
	{
		expanded = expand;
		content.setVisible(expand);
		arrow.setIcon(expand ? EXPANDED : COLLAPSED);
		revalidate();
		repaint();

		if (expand && onExpanded != null)
		{
			onExpanded.run();
		}
	}

	/**
	 * Tracks the content rather than reporting an unbounded height, so a stack of these in a
	 * {@code BoxLayout} does not stretch the collapsed ones to share the panel equally.
	 */
	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}
}
