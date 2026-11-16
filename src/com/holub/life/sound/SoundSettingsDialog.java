package com.holub.life.sound;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;

import com.holub.life.sound.SoundSettings.Feature;
import com.holub.life.sound.SoundSettings.Instrument;

/**
 * 사운드 기능마다 한 줄씩, 켜고 끄는 체크박스와 고를 수 있으면 악기
 * 선택 상자를 보여 주는 창. 메뉴가 열려 있으면 게임이 멈추기 때문에
 * 게임을 실행하는 동안 띄워 둘 수 있게 별도 창으로 만들었다.
 */
final class SoundSettingsDialog extends JDialog
{
	private static final int GAP = 6;

	SoundSettingsDialog( Window owner, SoundSettings settings )
	{	super( owner, "Sound Settings" );
		setDefaultCloseOperation( HIDE_ON_CLOSE );

		JPanel rows = new JPanel( new GridBagLayout() );
		rows.setBorder( BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP) );

		int row = 0;
		for( Feature feature : Feature.values() )
			addRow( rows, row++, feature, settings );

		setContentPane( rows );
		pack();
		setResizable( false );
	}

	private void addRow( JPanel rows, int row, Feature feature,
						 SoundSettings settings )
	{
		JCheckBox checkBox =
				new JCheckBox( feature.label, settings.isOn(feature) );
		checkBox.addActionListener
		(	e -> settings.setOn( feature, checkBox.isSelected() )
		);
		rows.add( checkBox, cell(0, row) );

		if( feature.instruments.isEmpty() )
			return;

		JComboBox<Instrument> picker = new JComboBox<>(
						feature.instruments.toArray(new Instrument[0]) );
		picker.setSelectedItem( settings.instrumentOf(feature) );
		picker.addActionListener
		(	e -> settings.setInstrument(
						feature, (Instrument)picker.getSelectedItem() )
		);
		rows.add( picker, cell(1, row) );
	}

	private GridBagConstraints cell( int column, int row )
	{	GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx	= column;
		constraints.gridy	= row;
		constraints.fill	= GridBagConstraints.HORIZONTAL;
		constraints.anchor	= GridBagConstraints.WEST;
		constraints.insets	= new Insets( 2, GAP, 2, GAP );
		return constraints;
	}
}
