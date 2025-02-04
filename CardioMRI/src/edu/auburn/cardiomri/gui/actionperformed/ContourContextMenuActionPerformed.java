package edu.auburn.cardiomri.gui.actionperformed;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JPopupMenu;

import edu.auburn.cardiomri.gui.models.ImageModel;
import edu.auburn.cardiomri.gui.views.GridControlView;
import edu.auburn.cardiomri.gui.views.ModeView;
import edu.auburn.cardiomri.popupmenu.view.ContourContextMenu;
import edu.auburn.cardiomri.util.Mode;

public class ContourContextMenuActionPerformed implements ActionListener {

	private ImageModel imageModel;
	private JPopupMenu menu;
	
	
	public ContourContextMenuActionPerformed(ImageModel imageModel, JPopupMenu menu){
		this.imageModel = imageModel;
		this.menu = menu;
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		String actionCommand = e.getActionCommand();
		
		if(actionCommand.equalsIgnoreCase("Delete Contour")){
			imageModel.deleteSelectedContour();
			Mode.setMode(Mode.selectMode());
			GridControlView.depressToggles();
		}
		else if(actionCommand.equalsIgnoreCase("Hide Contour")){
			System.out.println(imageModel.getSelectedContour().toString());
			
			imageModel.hideSelectedContour();
			Mode.setMode(Mode.selectMode());
			GridControlView.depressToggles();
		}
		else if(actionCommand.equalsIgnoreCase("Hide All Contours")){
			System.out.println(imageModel.getSelectedContour().toString());
			
			imageModel.hideAllContours();
			Mode.setMode(Mode.selectMode());
			GridControlView.depressToggles();
		}
		else if(actionCommand.equalsIgnoreCase("Lock Smooth")){
			imageModel.setControlPointLocked(true);
		}
		else if(actionCommand.equalsIgnoreCase("Unlock Smooth")){
			imageModel.setControlPointLocked(false);
		}
		else if(actionCommand.equalsIgnoreCase("Delete Point")){

			imageModel.deleteControlPoint(
					imageModel.getSelectedControlPoint().getX(), 
					imageModel.getSelectedControlPoint().getY());
			Mode.setMode(Mode.selectMode());
			
		}
		else if(actionCommand.equalsIgnoreCase("Done Adding")){
			imageModel.setSelectedContour(null);
			menu.setVisible(false);
			Mode.setMode(Mode.selectMode());
			GridControlView.depressToggles();
		}
		
		menu.setVisible(false);

	}

}
