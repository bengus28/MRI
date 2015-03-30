package edu.auburn.cardiomri.gui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Observable;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.display.SingleImagePanel;


import edu.auburn.cardiomri.datastructure.Contour;
import edu.auburn.cardiomri.datastructure.DICOMImage;
import edu.auburn.cardiomri.datastructure.Contour.Type;
import edu.auburn.cardiomri.gui.ConstructImage;

public class ImageView implements java.util.Observer {

	private JPanel panel;

	private ImageDisplay display = null;
	private Vector<Contour> contours;
	private Contour contourObject = new Contour(Contour.Type.DEFAULT), currentContour;

	// Observer methods
	@Override
	public void update(Observable obs, Object obj) {

		if (obj.getClass() == DICOMImage.class) { 

			this.display = null;
			
			SingleImagePanel.deconstructAllSingleImagePanelsInContainer(this.panel);

			this.panel.removeAll();
		
			DICOMImage dImage = ((DICOMImage) obj);
		
			ConstructImage sImg = null;
			
			try {
				sImg = new ConstructImage(dImage);
				
				this.display = new ImageDisplay(sImg);
				this.contours = dImage.getContours();
				

			} catch (DicomException e) {
				e.printStackTrace();
			}
			
			this.panel.add(display);
			this.panel.revalidate();
			this.display.revalidate();
			this.display.repaint();		
			this.display.setContours(this.contours);
			this.display.repaint();		
		}
		
		if(obj instanceof Vector<?>)
		{
			if(((Vector<Contour>) obj).firstElement().getClass() == contourObject.getClass())
			{
				this.contours = (Vector<Contour>) obj;
				if(this.display != null){
					System.out.println("new list of contours");
					this.display.setContours(contours);
					this.display.setPreDefinedShapes(contours);
					this.display.repaint();
				}
			}
		}
		
		if(obj.getClass() == contourObject.getClass())
		{
			if(this.display != null){
				this.display.setCurrentContour((Contour) obj);
				this.display.repaint();
			}
			currentContour = (Contour) obj;
		}
	}


	// Getters
	/*
	 * Returns the class panel attribute.
	 *
	 * @return The class' panel attribute.
	 */

	public JPanel getPanel() { 
		return this.panel; 
	}
	
	// Constructors
	public ImageView() {
		//System.out.println("ImageView()");
		
		this.panel = new JPanel();
		this.panel.setLayout(new GridLayout(1, 1));
		this.panel.setBackground(Color.BLACK);
		this.panel.setOpaque(true);
		this.panel.setVisible(true);
	}
	
	public ImageDisplay getImageDisplay() {
		return this.display;
	}

}
