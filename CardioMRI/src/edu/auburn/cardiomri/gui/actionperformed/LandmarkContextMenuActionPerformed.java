package edu.auburn.cardiomri.gui.actionperformed;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPopupMenu;

import edu.auburn.cardiomri.gui.models.ImageModel;
import edu.auburn.cardiomri.gui.views.Toast;
import edu.auburn.cardiomri.util.Mode;

public class LandmarkContextMenuActionPerformed implements ActionListener{

	private ImageModel imageModel;
	private JPopupMenu menu;
	
	public LandmarkContextMenuActionPerformed(ImageModel imageModel, JPopupMenu menu){
		this.imageModel = imageModel;
		this.menu = menu;
	}
	
	
	@Override
	public void actionPerformed(ActionEvent e) {
		String action = e.getActionCommand();
		
		if(action.equals("Done Adding")){
			Mode.setMode(Mode.selectMode());
			new Toast(Mode.modeToast());
			menu.setVisible(false);
			
		}
		else if(action.equals("Delete Landmark")){
			
			Mode.setMode(Mode.selectMode());
			new Toast(Mode.modeToast());
			menu.setVisible(false);
		}
		else if(action.equals("Hide Landmark")){
			
		}
		
		
	}

}
