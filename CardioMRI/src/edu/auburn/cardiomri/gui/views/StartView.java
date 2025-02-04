package edu.auburn.cardiomri.gui.views;

import java.awt.Cursor;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.sun.javafx.scene.paint.GradientUtils.Point;

import edu.auburn.cardiomri.dataimporter.DICOM3Importer;
import edu.auburn.cardiomri.dataimporter.DICOMFileTreeWalker;
import edu.auburn.cardiomri.datastructure.DICOMImage;
import edu.auburn.cardiomri.datastructure.Study;
import edu.auburn.cardiomri.datastructure.Study.NotInStudyException;
import edu.auburn.cardiomri.gui.models.StartModel;
import edu.auburn.cardiomri.util.StudyUtilities;

/**
 * 
 * This view is the first view of the project. A study or a single image
 *  will be selected from the fileChooser. 
 *	
 *
 */
public class StartView extends View {
	
	Cursor waitCursor = new Cursor(Cursor.WAIT_CURSOR);
	Cursor defaultCursor = new Cursor(Cursor.DEFAULT_CURSOR);

	protected JFileChooser fileChooser;
	
	/**
	 * Adds three buttons on opening screen and adds action 
	 * listeners to each of those buttons
	 * 
	 */
	public StartView()
	{
		super();
        this.panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints(); //creates grid
        
        //Add the three buttons
        JButton newStudy = new JButton("New Study");
        newStudy.setActionCommand("Create New Study");
        newStudy.addActionListener(this);
        c.weightx = 0;
        c.gridx = 0;
        c.gridy = 1;
        this.panel.add(newStudy, c);
        
        JButton existingStudy = new JButton("Existing Study");
        existingStudy.setActionCommand("Load Existing Study");
        existingStudy.addActionListener(this);
        c.weightx = 0;
        c.gridx = 1;
        c.gridy = 1;
        this.panel.add(existingStudy, c);
        
        
        JButton singleImage = new JButton("Single Image");
        singleImage.setActionCommand("Load Single DICOM");
        singleImage.addActionListener(this);
        c.weightx = 0;
        c.gridx = 2;
        c.gridy = 1;
        this.panel.add(singleImage, c);

        Icon icon = new ImageIcon("icons/LoadingHeart.gif");
        JLabel label = new JLabel(icon);
        c.weightx = 0;
        c.gridx = 1;
        c.gridy = 0;
        this.panel.add(label, c);
        

       
        fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
	}
	
	/**
	 * Performs a command when the action listener registers a 
	 * button click
	 * 
	 */
	public void actionPerformed(java.awt.event.ActionEvent e) {

	    String actionCommand = e.getActionCommand();
	    
	    if (actionCommand.equals("Create New Study")) {
	        this.createNewStudy();
	    } else if (actionCommand.equals("Load Existing Study")) {
	    	this.loadStudy();
	    } else if (actionCommand.equals("Load Single DICOM")) {
	        try {
	            this.loadSingleDicom();
	        } catch (NotInStudyException e1) {
	            e1.printStackTrace();
	        }
	    }
	}
    
    /**
     * Opens a JFileChooser that allows the user to select a Study model file
     * (.smc).
     */
    public void loadStudy() {
    	fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        FileFilter studyFilter = new FileNameExtensionFilter(
                "Study file (.smc)", "smc");
        fileChooser.setFileFilter(studyFilter);

        int response = fileChooser.showOpenDialog(this.panel);
        if (response == JFileChooser.APPROVE_OPTION) {
        	
        	setCursorWait();
        	
            String fileName = fileChooser.getSelectedFile().getAbsolutePath();

            Study study = StudyUtilities.loadStudy(fileName);
            this.getStartModel();
			StartModel.setLoadStudy();
            this.getStartModel().setStudy(study);
        }
    }

    
    /**
     * Opens a JFileChooser that allows the user to select a Directory, which
     * will then be iterated through to generate a new Study object.
     *
     */
    public void createNewStudy() {
    	
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int returnVal = fileChooser.showOpenDialog(this.panel);
        


        if (returnVal == JFileChooser.APPROVE_OPTION) {
        
        	setCursorWait();
        	
            String directory = fileChooser.getSelectedFile().getAbsolutePath();
            
            Path path = Paths.get(directory);
            
            DICOMFileTreeWalker fileTreeWalker = new DICOMFileTreeWalker();
            
            Study study = fileTreeWalker.addFileTreeToStudy(path, new Study());
            
            this.getStartModel().setStudy(study);

            this.panel.repaint();
            
        } else {
            // System.out.println("FileChooser : Canceled choosing directory");
        }
        
        
        
    }

    /**
     * Opens a JFileChooser that allows the user to select a single Dicom file
     * and generate a Study object with the Dicom as the only image in it.
     * 
     * @throws NotInStudyException 
     */
    public void loadSingleDicom() throws NotInStudyException {

        FileFilter dicomType = new FileNameExtensionFilter("DICOM file (.dcm)","dcm");
        
        fileChooser.addChoosableFileFilter(dicomType);

        int returnVal = fileChooser.showOpenDialog(this.panel);
        
        if (returnVal == JFileChooser.APPROVE_OPTION) {
        	
        	setCursorWait();
        	
            String filename = fileChooser.getSelectedFile().getPath();

            DICOMImage dImage = DICOM3Importer.makeDICOMImageFromFile(filename);

            Study study = new Study();
            
            study.addImage(dImage);

            this.getStartModel().setStudy(study);
        } else {
        	
            // System.out.println("GUIController : Cancel choosing file");
        }
    }
   
    public StartModel getStartModel()
    {
    	return (StartModel) this.model;
    }
    
    //Sets the cursor to a waiting cursor
    private void setCursorWait() {
    	
    	// These 4 lines refresh the frame so the cursor will change on a Mac
        // We tried dozens of ideas but this was the only thing to work
        this.panel.setCursor(waitCursor);
        JFrame topFrame = ((JFrame) SwingUtilities.getWindowAncestor(this.panel));
        topFrame.setVisible(false);
        topFrame.setVisible(true);
        /////////////////
        
    }
    
    //Sets the cursor to the default pointer
    
    private void setCursorDefault() {
    	
       	// These 4 lines refresh the frame so the cursor will change on a Mac
        // We tried dozens of ideas but this was the only thing to work
        this.panel.setCursor(defaultCursor);
        JFrame topFrame = ((JFrame) SwingUtilities.getWindowAncestor(this.panel));
        topFrame.setVisible(false);
        topFrame.setVisible(true);
        /////////////////
    }
    
    
}
