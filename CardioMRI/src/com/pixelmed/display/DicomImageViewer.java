/* Copyright (c) 2001-2013, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.display;

import java.awt.*; 
import java.awt.event.*; 
//import java.awt.image.*; 
//import java.awt.color.*; 
import java.util.*; 
import java.io.*;
import javax.swing.*; 
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.border.*;

import com.OSXAdapter;

import com.pixelmed.display.event.*; 
import com.pixelmed.display.event.FrameSelectionChangeEvent; 
import com.pixelmed.display.event.FrameSortOrderChangeEvent; 
import com.pixelmed.display.event.StatusChangeEvent; 
import com.pixelmed.display.event.WellKnownContext; 

import com.pixelmed.dicom.*;
import com.pixelmed.event.ApplicationEventDispatcher; 
import com.pixelmed.event.EventContext;
import com.pixelmed.event.SelfRegisteringListener;
import com.pixelmed.validate.*;
import com.pixelmed.database.*;
import com.pixelmed.network.*;
import com.pixelmed.query.*;
import com.pixelmed.utils.*;
//import com.pixelmed.transfermonitor.TransferMonitor;

// for localizer ...

import com.pixelmed.geometry.*;

/**
 * <p>This class is an entire application for displaying and viewing images and
 * spectroscopy objects.</p>
 * 
 * <p>It supports a local database of DICOM objects, as well as the ability to
 * read a load from a DICOMDIR, and to query and retrieve objects across the
 * network.</p>
 * 
 * <p>It is configured by use of a properties file that resides in the user's
 * home directory in <code>.com.pixelmed.display.DicomImageViewer.properties</code>.</p>
 * 
 * @author	dclunie
 */
public class DicomImageViewer extends ApplicationFrame implements 
		KeyListener,MouseListener
	{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/***/
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/display/DicomImageViewer.java,v 1.232 2014/06/08 23:33:42 dclunie Exp $";

	/***/
	static final char screenSnapShotKeyChar = 'K';

	/***/
	static final String propertiesFileName  = ".com.pixelmed.display.DicomImageViewer.properties";

	// property names ...

	/***/
	private static final String propertyName_DicomCurrentlySelectedStorageTargetAE = "Dicom.CurrentlySelectedStorageTargetAE";
	/***/
	private static final String propertyName_DicomCurrentlySelectedQueryTargetAE = "Dicom.CurrentlySelectedQueryTargetAE";

	/***/
	static final String propertyName_FullScreen = "Display.FullScreen";

	private DatabaseApplicationProperties databaseApplicationProperties = null;
	private NetworkApplicationProperties networkApplicationProperties = null;
	private NetworkApplicationInformation networkApplicationInformation = null;

	protected StoredFilePathStrategy storedFilePathStrategy = StoredFilePathStrategy.BYSOPINSTANCEUIDHASHSUBFOLDERS;
		
	protected File savedImagesFolder;
	protected String lastDirectoryPath;
	protected JPanel multiPanel;
	protected JList displayListOfPossibleReferenceImagesForImages;
	protected JList displayListOfPossibleBackgroundImagesForSpectra;
	protected JList displayListOfPossibleReferenceImagesForSpectra;
	protected JScrollPane databaseTreeScrollPane;
	protected JScrollPane dicomdirTreeScrollPane;
	protected JScrollPane scrollPaneOfCurrentAttributes;
	protected JScrollPane attributeFrameTableScrollPane;
	protected JScrollPane attributeTreeScrollPane;
	protected JScrollPane queryTreeScrollPane;
	protected JScrollPane structuredReportTreeScrollPane;

	protected SafeCursorChanger cursorChanger;

	public void quit() {
		// close of database and unregistering DNS services now done in ShutdownHook.run()
		dispose();
		System.exit(0);
	}

	// implement KeyListener methods
	
	/**
	 * @param	e
	 */
	public void keyPressed(KeyEvent e) {
//System.err.println("Key pressed event"+e);
	}

	/**
	 * @param	e
	 */
	public void keyReleased(KeyEvent e) {
//System.err.println("Key released event"+e);
	}

	/**
	 * @param	e
	 */
	public void keyTyped(KeyEvent e) {
//System.err.println("Key typed event "+e);
//System.err.println("Key typed char "+e.getKeyChar());
		if (e.getKeyChar() == screenSnapShotKeyChar) {
			Rectangle extent = this.getBounds();
			File snapShotFile = takeSnapShot(extent);
System.err.println("Snapshot to file "+snapShotFile);
		}
	}

	/**
	 * @param	e
	 */
	public void mouseClicked(MouseEvent e) {}

	/**
	 * @param	e
	 */
	public void mouseEntered(MouseEvent e) {
//System.err.println("mouseEntered event"+e);
		requestFocus();		// In order to allow us to receive KeyEvents
	}

	/**
	 * @param	e
	 */
	public void mouseExited(MouseEvent e) {}
	/**
	 * @param	e
	 */
	public void mousePressed(MouseEvent e) {}
	/**
	 * @param	e
	 */
	public void mouseReleased(MouseEvent e) {}

	// DicomImageViewer specific methods ...
	
	/**
	 * Implement interface to status bar for utilities to log messages to. 
	 */
	private class OurMessageLogger implements MessageLogger {
		public void send(String message) {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(message));
		}
		
		public void sendLn(String message) {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(message));
		}
	}

	//private Font defaultFont;

	/***/
	private DatabaseInformationModel databaseInformationModel;

	/***/
	private static final int widthWantedForBrowser = 400;		// check wide enough for FlowLayout buttons else they will be cut off
	/***/
	private static final int heightWantedForAttributeTable = 76;
	/***/
	private static final Dimension defaultMultiPanelDimension = new Dimension(512,512);
	
	/***/
	private int applicationWidth;
	/***/
	private int applicationHeight;

	/***/
	private int imagesPerRow;
	/***/
	private int imagesPerCol;
	
	/***/
	private ImageLocalizerManager imageLocalizerManager;
	/***/
	private SpectroscopyLocalizerManager spectroscopyLocalizerManager;
	
	// Stuff to support panel that displays contents of attributes of current frame ...
	
	/***/
	private AttributeListFunctionalGroupsTableModelOneFrame modelOfCurrentAttributesForCurrentFrameBrowser;
	/***/
	private AttributeListTableBrowser tableOfCurrentAttributesForCurrentFrameBrowser;
	
	/***/
	private void createTableOfCurrentAttributesForCurrentFrameBrowser () {
		HashSet excludeList = new HashSet();
		excludeList.add(TagFromName.FileMetaInformationGroupLength);
		excludeList.add(TagFromName.ImplementationVersionName);
		excludeList.add(TagFromName.SourceApplicationEntityTitle);
		modelOfCurrentAttributesForCurrentFrameBrowser = new AttributeListFunctionalGroupsTableModelOneFrame(null,null,excludeList);	// list of attributes set later when image selected
		tableOfCurrentAttributesForCurrentFrameBrowser = new AttributeListTableBrowser(modelOfCurrentAttributesForCurrentFrameBrowser);
		tableOfCurrentAttributesForCurrentFrameBrowser.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);					// otherwise horizontal scroll doesn't work
	}
	
	/***/
	private AttributeListTableBrowser getTableOfCurrentAttributesForCurrentFrameBrowser() { return tableOfCurrentAttributesForCurrentFrameBrowser; }
	/***/
	private AttributeListFunctionalGroupsTableModelOneFrame getModelOfCurrentAttributesForCurrentFrameBrowser() { return modelOfCurrentAttributesForCurrentFrameBrowser; }
	
	// Stuff to support panel that displays contents of per-frame varying attributes for all frames ...
	
	/***/
	private AttributeListFunctionalGroupsTableModelAllFrames modelOfCurrentAttributesForAllFramesBrowser;
	/***/
	private AttributeListTableBrowser tableOfCurrentAttributesForAllFramesBrowser;
	
	/***/
	private void createTableOfCurrentAttributesForAllFramesBrowser () {
		//HashSet excludeList = new HashSet();
		//excludeList.add(TagFromName.FileMetaInformationGroupLength);
		//excludeList.add(TagFromName.ImplementationVersionName);
		//excludeList.add(TagFromName.SourceApplicationEntityTitle);
		HashSet excludeList = null;
		modelOfCurrentAttributesForAllFramesBrowser = new AttributeListFunctionalGroupsTableModelAllFrames(null,null,excludeList);	// list of attributes set later when image selected
		tableOfCurrentAttributesForAllFramesBrowser = new AttributeListTableBrowser(modelOfCurrentAttributesForAllFramesBrowser);
		tableOfCurrentAttributesForAllFramesBrowser.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);					// otherwise horizontal scroll doesn't work
	}
	
	/***/
	private AttributeListTableBrowser getTableOfCurrentAttributesForAllFramesBrowser() { return tableOfCurrentAttributesForAllFramesBrowser; }
	/***/
	private AttributeListFunctionalGroupsTableModelAllFrames getModelOfCurrentAttributesForAllFramesBrowser() { return modelOfCurrentAttributesForAllFramesBrowser; }
	
	// Stuff to support references to things from within an instance
	
	/***/
	private DicomDirectory currentDicomDirectory;

	// implement FrameSelectionChangeListener to update attribute browser and current frame for (new) localizer posting when frame changes ...
	
	/***/
	private OurFrameSelectionChangeListener mainPanelFrameSelectionChangeListener;

	// The following changes currentSourceIndex and modelOfCurrentAttributesForCurrentFrameBrowser
	// so it is used ONLY for the main panel, and not the reference panel
	
	class OurFrameSelectionChangeListener extends SelfRegisteringListener {
	
		public OurFrameSelectionChangeListener(EventContext eventContext) {
			super("com.pixelmed.display.event.FrameSelectionChangeEvent",eventContext);
//System.err.println("DicomImageViewer.OurFrameSelectionChangeListener():");
		}
		
		/**
		 * @param	e
		 */
		public void changed(com.pixelmed.event.Event e) {
			FrameSelectionChangeEvent fse = (FrameSelectionChangeEvent)e;
//System.err.println("DicomImageViewer.OurFrameSelectionChangeListener.changed(): event="+fse);
			currentSourceIndex = fse.getIndex();	// track this for when a new localizer is selected for posting - fix for [bugs.mrmf] (000074)
			// DO remap currentSourceIndex through currentSourceSortOrder
			if (currentSourceSortOrder != null) {
				currentSourceIndex=currentSourceSortOrder[currentSourceIndex];
			}
			getModelOfCurrentAttributesForCurrentFrameBrowser().selectValuesForDifferentFrame(currentSourceIndex);
			getTableOfCurrentAttributesForCurrentFrameBrowser().setColumnWidths();
		}
	}
	
	// implement FrameSortOrderChangeListener to update attribute browser when sorted order changes ...
	
	/***/
	private OurFrameSortOrderChangeListener mainPanelFrameSortOrderChangeListener;

	class OurFrameSortOrderChangeListener extends SelfRegisteringListener {
	
		public OurFrameSortOrderChangeListener(EventContext eventContext) {
			super("com.pixelmed.display.event.FrameSortOrderChangeEvent",eventContext);
//System.err.println("DicomImageViewer.OurFrameSortOrderChangeListener():");
		}
		
		/**
		 * @param	e
		 */
		public void changed(com.pixelmed.event.Event e) {
			FrameSortOrderChangeEvent fso = (FrameSortOrderChangeEvent)e;
//System.err.println("DicomImageViewer.OurFrameSortOrderChangeListener.changed(): event="+fso);
			currentSourceIndex = fso.getIndex();		// track this for when a new localizer is selected for posting - fix for [bugs.mrmf] (000074)
			currentSourceSortOrder  = fso.getSortOrder();
			// DO NOT remap currentSourceIndex through currentSourceSortOrder
			//if (currentSourceSortOrder != null) {
			//	currentSourceIndex=currentSourceSortOrder[currentSourceIndex];
			//}
			getModelOfCurrentAttributesForCurrentFrameBrowser().selectValuesForDifferentFrame(currentSourceIndex);
			getTableOfCurrentAttributesForCurrentFrameBrowser().setColumnWidths();
		}
	}
	
	// track these so that they are known when a new localizer is selected for posting - fix for [bugs.mrmf] (000074)
	/***/
	private int currentSourceIndex;			// This is the index BEFORE remapping through currentSourceSortOrder
	/***/
	private int[] currentSourceSortOrder;

	// implement BrowserPaneChangeListener method ... events come from ourselves, not elsewhere

	//private JTabbedPane browserPane;
	//private JPanel displayControlsPanel;
	//private JPanel dicomdirControlsPanel;
	//private JPanel databaseControlsPanel;
	//private JPanel queryControlsPanel;
	//private JPanel spectroscopyControlsPanel;
	//private JPanel structuredReportTreeControlsPanel;

	private SourceImageVOILUTSelectorPanel sourceImageVOILUTSelectorPanel;
	private SourceImageWindowLinearCalculationSelectorPanel sourceImageWindowLinearCalculationSelectorPanel;
	private SourceImageWindowingAccelerationSelectorPanel sourceImageWindowingAccelerationSelectorPanel;
	private SourceImageGraphicDisplaySelectorPanel sourceImageGraphicDisplaySelectorPanel;
	private SourceImageShutterSelectorPanel sourceImageShutterSelectorPanel;

	
	// methods to do the work ...
	
	/**
	 * @param	attributeList
	 */
	private GeometryOfVolume getNewGeometryOfVolume(AttributeList attributeList) {
		GeometryOfVolume imageGeometry = null;
		try {
			imageGeometry = new GeometryOfVolumeFromAttributeList(attributeList);
		}
		catch (Throwable e) {	// NoClassDefFoundError may be thrown if no vecmath support available, which is an Error, not an Exception
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(e.toString()));
			//System.err.println(ex);
			e.printStackTrace(System.err);
		}
		return imageGeometry;
	}
	
	/**
	 * @param	attributeList
	 */
	private SpectroscopyVolumeLocalization getNewSpectroscopyVolumeLocalization(AttributeList attributeList) {
		SpectroscopyVolumeLocalization spectroscopyVolumeLocalization = null;
		try {
			spectroscopyVolumeLocalization = new SpectroscopyVolumeLocalization(attributeList);
		}
		catch (DicomException e) {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(e.toString()));
			//System.err.println(ex);
			e.printStackTrace(System.err);
		}
		return spectroscopyVolumeLocalization;
	}

	/**
	 * @param	dicomFileName
	 */
	private void loadBackgroundImageForSpectra(String dicomFileName) {
		AttributeList list = new AttributeList();
		SourceImage sImg = null;
		try {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Loading background image ..."));
			File file = FileUtilities.getFileFromNameInsensitiveToCaseIfNecessary(dicomFileName);
			DicomInputStream i = new DicomInputStream(file);
			list.read(i);
			i.close();
			String sopClassUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.SOPClassUID);
			if (SOPClass.isImageStorage(sopClassUID)) {
				sImg = new SourceImage(list);
			}
		}
		catch (Exception e) {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(e.toString()));
			//System.err.println(e);
			e.printStackTrace(System.err);
		}
		finally {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
		}

		if (sImg != null) {		
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(
				new SourceImageSelectionChangeEvent(WellKnownContext.SPECTROSCOPYBACKGROUNDIMAGE,sImg,null/*sortOrder*/,0,list,getNewGeometryOfVolume(list)));
		}
	}
	
	/**
	 * @param	dicomFileName
	 * @param	referenceImagePanel
	 * @param	spectroscopy
	 */
	private void loadReferenceImagePanel(String dicomFileName,JPanel referenceImagePanel,boolean spectroscopy) {
		AttributeList list = new AttributeList();
		SourceImage sImg = null;
		try {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Loading referenced image ..."));
			File file = FileUtilities.getFileFromNameInsensitiveToCaseIfNecessary(dicomFileName);
			DicomInputStream i = new DicomInputStream(file);
			list.read(i);
			i.close();
			String sopClassUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.SOPClassUID);
			if (SOPClass.isImageStorage(sopClassUID)) {
				//referenceImagePanel.removeAll();
				sImg = new SourceImage(list);
			}
		}
		catch (Exception e) {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(e.toString()));
			//System.err.println(e);
			e.printStackTrace(System.err);
		}
		finally {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
		}
	
		if (sImg != null && sImg.getNumberOfBufferedImages() > 0) {		
			GeometryOfVolume imageGeometry = getNewGeometryOfVolume(list);

			SingleImagePanel ip = new SingleImagePanel(sImg,WellKnownContext.REFERENCEPANEL,imageGeometry);

			ip.setOrientationAnnotations(new OrientationAnnotations(list,imageGeometry),"SansSerif",Font.PLAIN,8,Color.pink);

			SingleImagePanel.deconstructAllSingleImagePanelsInContainer(referenceImagePanel);
			referenceImagePanel.removeAll();
			referenceImagePanel.add(ip);
			//imagePanel[0]=ip;

			if (spectroscopy) {
				spectroscopyLocalizerManager.setReferenceImagePanel(ip);
			}
			else {
				imageLocalizerManager.setReferenceImagePanel(ip);
			}
			
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(
				new SourceImageSelectionChangeEvent(WellKnownContext.REFERENCEPANEL,sImg,null/*sortOrder*/,0,list,imageGeometry));
				
			// One must now reselect the current frame to trigger the current (rather than the first) frame to be shown on the localizer
			//
			// This is fix for [bugs.mrmf] (000074)
			//
			// The values for currentSourceSortOrder,currentSourceIndex
			// have been cached by the DicomImageViewer class whenever it receives a FrameSelectionChangeEvent or FrameSortOrderChangeEvent
			//
			// NB. It is important that currentSourceIndex NOT have been remapped through currentSourceSortOrder yet

			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(
				new FrameSelectionChangeEvent(WellKnownContext.MAINPANEL,currentSourceIndex));
		}
	}
	
	/**
	 * @param	multiPanel
	 * @param	sImg
	 * @param	list
	 */
	private void loadMultiPanelWithImage(JPanel multiPanel,SourceImage sImg,AttributeList list) {
//System.err.println("DicomImageViewer.loadMultiPanelWithImage():");
		if (sImg != null && sImg.getNumberOfBufferedImages() > 0) {
			GeometryOfVolume imageGeometry = getNewGeometryOfVolume(list);
			PixelSpacing pixelSpacing = new PixelSpacing(list,imageGeometry);
			//SingleImagePanel ip = new SingleImagePanel(sImg,WellKnownContext.MAINPANEL,imageGeometry);
			SingleImagePanel ip = new SingleImagePanelWithLineDrawing(sImg,WellKnownContext.MAINPANEL,imageGeometry);
			//SingleImagePanel ip = new SingleImagePanelWithRegionDetection(sImg,WellKnownContext.MAINPANEL,imageGeometry);
			ip.setPixelSpacingInSourceImage(pixelSpacing.getSpacing(),pixelSpacing.getDescription());
			
			ip.setDemographicAndTechniqueAnnotations(new DemographicAndTechniqueAnnotations(list,imageGeometry),"SansSerif",Font.PLAIN,10,Color.pink);
			ip.setOrientationAnnotations(new OrientationAnnotations(list,imageGeometry),"SansSerif",Font.PLAIN,20,Color.pink);
			
			sourceImageVOILUTSelectorPanel.sendEventCorrespondingToCurrentButtonState();				// will get to new SingleImagePanel via MainImagePanelVOILUTSelectionEventSink
			sourceImageWindowLinearCalculationSelectorPanel.sendEventCorrespondingToCurrentButtonState();
			sourceImageWindowingAccelerationSelectorPanel.sendEventCorrespondingToCurrentButtonState();
			sourceImageGraphicDisplaySelectorPanel.sendEventCorrespondingToCurrentButtonState();

			SingleImagePanel.deconstructAllSingleImagePanelsInContainer(multiPanel);
			SpectraPanel.deconstructAllSpectraPanelsInContainer(multiPanel);
			multiPanel.removeAll();
			multiPanel.add(ip);
			//imagePanel[0]=ip;

			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(
				new SourceImageSelectionChangeEvent(WellKnownContext.MAINPANEL,sImg,null/*sortOrder*/,0,list,imageGeometry));
		}
	}
	

	/**
	 * @param	multiPanel
	 * @param	sSpectra
	 * @param	list
	 */
	private Dimension loadMultiPanelWithSpectra(JPanel multiPanel,SourceSpectra sSpectra,AttributeList list) {
//System.err.println("loadMultiPanelWithSpectra:");
		Dimension multiPanelDimension = null;
		if (sSpectra != null) {
			float[][] spectra = sSpectra.getSpectra();

			GeometryOfVolume spectroscopyGeometry = getNewGeometryOfVolume(list);
			SpectroscopyVolumeLocalization spectroscopyVolumeLocalization = getNewSpectroscopyVolumeLocalization(list);

			SpectraPanel sp = new SpectraPanel(spectra,sSpectra.getRows(),sSpectra.getColumns(),sSpectra.getMinimum(),sSpectra.getMaximum(),
				spectroscopyGeometry,spectroscopyVolumeLocalization,
				WellKnownContext.MAINPANEL,WellKnownContext.SPECTROSCOPYBACKGROUNDIMAGE);

			SingleImagePanel.deconstructAllSingleImagePanelsInContainer(multiPanel);
			SpectraPanel.deconstructAllSpectraPanelsInContainer(multiPanel);
			multiPanel.removeAll();
			multiPanel.add(sp);
			
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(
				new SourceSpectrumSelectionChangeEvent(WellKnownContext.MAINPANEL,spectra,spectra.length,null,0,list,spectroscopyGeometry,spectroscopyVolumeLocalization));
		}
		return multiPanelDimension;
	}

	// keep track of the query information model in use ...

	/***/
	private QueryInformationModel currentRemoteQueryInformationModel;

	/**
	 * @param	remoteAEForQuery
	 * @param	browserPane
	 * @param	tabNumberOfRemoteInBrowserPane
	 */
	private void setCurrentRemoteQueryInformationModel(String remoteAEForQuery,JTabbedPane browserPane,int tabNumberOfRemoteInBrowserPane) {
		currentRemoteQueryInformationModel=null;
		String stringForTitle="";
		if (remoteAEForQuery != null && remoteAEForQuery.length() > 0 && networkApplicationProperties != null && networkApplicationInformation != null) {
			try {
				String              queryCallingAETitle = networkApplicationProperties.getCallingAETitle();
				String               queryCalledAETitle = networkApplicationInformation.getApplicationEntityTitleFromLocalName(remoteAEForQuery);
				PresentationAddress presentationAddress = networkApplicationInformation.getApplicationEntityMap().getPresentationAddress(queryCalledAETitle);
				
				if (presentationAddress == null) {
					throw new Exception("For remote query AE <"+remoteAEForQuery+">, presentationAddress cannot be determined");
				}
				
				String                        queryHost = presentationAddress.getHostname();
				int			      queryPort = presentationAddress.getPort();
				String                       queryModel = networkApplicationInformation.getApplicationEntityMap().getQueryModel(queryCalledAETitle);
				int                     queryDebugLevel = networkApplicationProperties.getQueryDebugLevel();
				
				if (NetworkApplicationProperties.isStudyRootQueryModel(queryModel) || queryModel == null) {
					currentRemoteQueryInformationModel=new StudyRootQueryInformationModel(queryHost,queryPort,queryCalledAETitle,queryCallingAETitle,queryDebugLevel);
					stringForTitle=":"+remoteAEForQuery;
				}
				else {
					throw new Exception("For remote query AE <"+remoteAEForQuery+">, query model "+queryModel+" not supported");
				}
			}
			catch (Exception e) {		// if an AE's property has no value, or model not supported
				e.printStackTrace(System.err);
			}
		}
		if (browserPane != null) {
			browserPane.setTitleAt(tabNumberOfRemoteInBrowserPane,"Remote"+stringForTitle);
		}
//System.err.println("DicomImageViewer.setCurrentRemoteQueryInformationModel(): now "+currentRemoteQueryInformationModel);
	}
	
	/***/
	private QueryInformationModel getCurrentRemoteQueryInformationModel() { return currentRemoteQueryInformationModel; }

	// keep track of current filter for use for queries

	/***/
	private AttributeList currentRemoteQueryFilter;
	/**
	 * @param	filter
	 */
	private void setCurrentRemoteQueryFilter(AttributeList filter) {currentRemoteQueryFilter=filter; }	
	/***/
	private AttributeList getCurrentRemoteQueryFilter() {
//System.err.println("DicomImageViewer.getCurrentRemoteQueryFilter(): now "+currentRemoteQueryFilter);
		return currentRemoteQueryFilter;
	}

	/***/
	private void initializeCurrentRemoteQueryFilter() {
		AttributeList filter = new AttributeList();
		setCurrentRemoteQueryFilter(filter);
		
		// specific character set is established and inserted later, when text values have been entered into the filter panel
		
		{ AttributeTag t = TagFromName.PatientName; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientID; Attribute a = new LongStringAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.PatientBirthDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientSex; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientBirthTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
		//kills Leonardo ... { AttributeTag t = TagFromName.OtherPatientIDs; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		//kills Leonardo ... { AttributeTag t = TagFromName.OtherPatientNames; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		//kills Leonardo ... { AttributeTag t = TagFromName.EthnicGroup; Attribute a = new ShortStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientComments; Attribute a = new LongTextAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.StudyID; Attribute a = new ShortStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.StudyDescription; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.OtherStudyNumbers; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PerformedProcedureStepID; Attribute a = new ShortStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PerformedProcedureStepStartDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PerformedProcedureStepStartTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.SOPClassesInStudy; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ModalitiesInStudy; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.StudyDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.StudyTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ReferringPhysicianName; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AccessionNumber; Attribute a = new ShortStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PhysiciansOfRecord; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.NameOfPhysiciansReadingStudy; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AdmittingDiagnosesDescription; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientAge; Attribute a = new AgeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientSize; Attribute a = new DecimalStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PatientWeight; Attribute a = new DecimalStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.Occupation; Attribute a = new ShortStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AdditionalPatientHistory; Attribute a = new LongTextAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.SeriesDescription; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.SeriesNumber; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.Modality; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.SeriesDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.SeriesTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.PerformingPhysicianName; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ProtocolName; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.OperatorsName; Attribute a = new PersonNameAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.Laterality; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.BodyPartExamined; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.Manufacturer; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ManufacturerModelName; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.StationName; Attribute a = new ShortStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.InstitutionName; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.InstitutionalDepartmentName; Attribute a = new LongStringAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.InstanceNumber; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ImageComments; Attribute a = new LongTextAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.ContentDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ContentTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ImageType; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AcquisitionNumber; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AcquisitionDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AcquisitionTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.AcquisitionDateTime; Attribute a = new DateTimeAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.DerivationDescription; Attribute a = new ShortTextAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.QualityControlImage; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.BurnedInAnnotation; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.LossyImageCompression; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.LossyImageCompressionRatio; Attribute a = new DecimalStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.LossyImageCompressionMethod; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.ContrastBolusAgent; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.NumberOfFrames; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.Rows; Attribute a = new UnsignedShortAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.Columns; Attribute a = new UnsignedShortAttribute(t); filter.put(t,a); }

		{ AttributeTag t = TagFromName.StudyInstanceUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.SeriesInstanceUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.SOPInstanceUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.SOPClassUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
		
		// Always good to insert these ... avoids premature nested query just to find number of node children in browser ...
		{ AttributeTag t = TagFromName.NumberOfStudyRelatedInstances; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.NumberOfStudyRelatedSeries; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
		{ AttributeTag t = TagFromName.NumberOfSeriesRelatedInstances; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
	}

	// keep track of current remote database selection in case someone wants to retrieve it ...
	
	/***/
	private QueryTreeRecord currentRemoteQuerySelectionQueryTreeRecord;
	/***/
	private AttributeList currentRemoteQuerySelectionUniqueKeys;
	/***/
	private Attribute currentRemoteQuerySelectionUniqueKey;
	/***/
	private String currentRemoteQuerySelectionRetrieveAE;
	/***/
	private String currentRemoteQuerySelectionLevel;
	/**
	 * @param	uniqueKeys
	 * @param	uniqueKey
	 * @param	identifier
	 */
	private void setCurrentRemoteQuerySelection(AttributeList uniqueKeys,Attribute uniqueKey,AttributeList identifier) {
		currentRemoteQuerySelectionUniqueKeys=uniqueKeys;
		currentRemoteQuerySelectionUniqueKey=uniqueKey;
		currentRemoteQuerySelectionRetrieveAE=null;
		if (identifier != null) {
			Attribute aRetrieveAETitle=identifier.get(TagFromName.RetrieveAETitle);
			if (aRetrieveAETitle != null) currentRemoteQuerySelectionRetrieveAE=aRetrieveAETitle.getSingleStringValueOrNull();
		}
		if (currentRemoteQuerySelectionRetrieveAE == null) {
			// it is legal for RetrieveAETitle to be zero length at all but the lowest levels of
			// the query model :( (See PS 3.4 C.4.1.1.3.2)
			// (so far the Leonardo is the only one that doesn't send it at all levels)
			// we could recurse down to the lower levels and get the union of the value there
			// but lets just keep it simple and ...
			// default to whoever it was we queried in the first place ...
			QueryInformationModel model=getCurrentRemoteQueryInformationModel();
			if (model != null) currentRemoteQuerySelectionRetrieveAE=model.getCalledAETitle();
		}
		currentRemoteQuerySelectionLevel = null;
		if (identifier != null) {
			Attribute a = identifier.get(TagFromName.QueryRetrieveLevel);
			if (a != null) {
				currentRemoteQuerySelectionLevel = a.getSingleStringValueOrNull();
			}
		}
		if (currentRemoteQuerySelectionLevel == null) {
			// QueryRetrieveLevel must have been (erroneously) missing in query response ... see with Dave Harvey's code on public server
			// so try to guess it from unique key in tree record
			// Fixes [bugs.mrmf] (000224) Missing query/retrieve level in C-FIND response causes tree select and retrieve to fail
			if (uniqueKey != null) {
				AttributeTag tag = uniqueKey.getTag();
				if (tag != null) {
					if (tag.equals(TagFromName.PatientID)) {
						currentRemoteQuerySelectionLevel="PATIENT";
					}
					else if (tag.equals(TagFromName.StudyInstanceUID)) {
						currentRemoteQuerySelectionLevel="STUDY";
					}
					else if (tag.equals(TagFromName.SeriesInstanceUID)) {
						currentRemoteQuerySelectionLevel="SERIES";
					}
					else if (tag.equals(TagFromName.SOPInstanceUID)) {
						currentRemoteQuerySelectionLevel="IMAGE";
					}
				}
			}
System.err.println("DicomImageViewer.setCurrentRemoteQuerySelection(): Guessed missing currentRemoteQuerySelectionLevel to be "+currentRemoteQuerySelectionLevel);
		}
	}

	/***/
	private QueryTreeRecord getCurrentRemoteQuerySelectionQueryTreeRecord() { return currentRemoteQuerySelectionQueryTreeRecord; }
	/***/
	private void setCurrentRemoteQuerySelectionQueryTreeRecord(QueryTreeRecord r) { currentRemoteQuerySelectionQueryTreeRecord=r; }

	/***/
	private AttributeList getCurrentRemoteQuerySelectionUniqueKeys() { return currentRemoteQuerySelectionUniqueKeys; }
	/***/
	private Attribute getCurrentRemoteQuerySelectionUniqueKey() { return currentRemoteQuerySelectionUniqueKey; }
	/***/
	private String getCurrentRemoteQuerySelectionRetrieveAE() { return currentRemoteQuerySelectionRetrieveAE; }
	/***/
	private String getCurrentRemoteQuerySelectionLevel() { return currentRemoteQuerySelectionLevel; }

	// Keep track of what is currently selected (e.g. in DICOMDIR) in case someone wants to load it ...
	
	/***/
	private Vector currentFilePathSelections;
	/**
	 * @param	filePathSelections
	 */
	private void setCurrentFilePathSelection(Vector filePathSelections) { currentFilePathSelections = filePathSelections; }
	/***/
	private String getCurrentFilePathSelection() { return (currentFilePathSelections != null && currentFilePathSelections.size() > 0) ? (String)(currentFilePathSelections.get(0)) : null; }
	/***/
	private Vector getCurrentFilePathSelections() { return currentFilePathSelections; }

	private DatabaseTreeRecord[] currentDatabaseTreeRecordSelections;
	private void setCurrentDatabaseTreeRecordSelections(DatabaseTreeRecord[] records) { currentDatabaseTreeRecordSelections = records; }
	private DatabaseTreeRecord[] getCurrentDatabaseTreeRecordSelections() { return currentDatabaseTreeRecordSelections; }

	// Keep track of what is currently actually loaded (e.g. in display) in case someone wants to import it into the database ...
	
	/***/
	private String currentlyDisplayedInstanceFilePath;
	/**
	 * @param	path
	 */
	private void setCurrentlyDisplayedInstanceFilePath(String path) { currentlyDisplayedInstanceFilePath = path; }
	/***/
	private String getCurrentlyDisplayedInstanceFilePath() { return currentlyDisplayedInstanceFilePath; }

	/***/
	private AttributeList currentAttributeListForDatabaseImport;
	/**
	 * @param	list
	 */
	private void setAttributeListForDatabaseImport(AttributeList list) { currentAttributeListForDatabaseImport = list; }
	/***/
	private AttributeList getAttributeListForDatabaseImport() { return currentAttributeListForDatabaseImport; }

	/***/
	private class OurDicomDirectoryBrowser extends DicomDirectoryBrowser {
		/**
		 * @param	list
		 * @param	imagePanel
		 * @param	referenceImagePanelForImages
		 * @param	referenceImagePanelForSpectra
		 * @exception	DicomException
		 */
		public OurDicomDirectoryBrowser(AttributeList list) throws DicomException {
			super(list,lastDirectoryPath,dicomdirTreeScrollPane,scrollPaneOfCurrentAttributes);
		}

		/**
		 * @param	paths
		 */
		protected void doSomethingWithSelectedFiles(Vector paths) {
			setCurrentFilePathSelection(paths);
		}

		/**
		 */
		protected void doSomethingMoreWithWhateverWasSelected() {
//System.err.println("DicomImageViewer.OurDicomDirectoryBrowser.doSomethingMoreWithWhateverWasSelected():");
			String dicomFileName = getCurrentFilePathSelection();
			if (dicomFileName != null) {
				loadDicomFileOrDirectory(dicomFileName,multiPanel,
					referenceImagePanelForImages,
					referenceImagePanelForSpectra);
			}
		}
	}

	/***/
	private class OurDatabaseTreeBrowser extends DatabaseTreeBrowser {
		/**
		 * @exception	DicomException
		 */
		public OurDatabaseTreeBrowser() throws DicomException {
			super(databaseInformationModel,databaseTreeScrollPane,scrollPaneOfCurrentAttributes);
		}

		protected boolean doSomethingWithSelections(DatabaseTreeRecord[] selections) {
			setCurrentDatabaseTreeRecordSelections(selections);
			return false;	// still want to call doSomethingWithSelectedFiles()
		}

		/**
		* @param	paths
		*/
		protected void doSomethingWithSelectedFiles(Vector paths) {
			setCurrentFilePathSelection(paths);
		}

		/**
		 */
		protected void doSomethingMoreWithWhateverWasSelected() {
//System.err.println("DicomImageViewer.OurDatabaseTreeBrowser.doSomethingMoreWithWhateverWasSelected():");
			String dicomFileName = getCurrentFilePathSelection();
			if (dicomFileName != null) {
				loadDicomFileOrDirectory(dicomFileName,multiPanel,
					referenceImagePanelForImages,
					referenceImagePanelForSpectra);
			}
		}
	}
	
	/***/
	private class OurQueryTreeBrowser extends QueryTreeBrowser {
		/**
		 * @param	q
		 * @param	m
		 * @param	treeBrowserScrollPane
		 * @param	attributeBrowserScrollPane
		 * @exception	DicomException
		 */
		OurQueryTreeBrowser(QueryInformationModel q,QueryTreeModel m,JScrollPane treeBrowserScrollPane,JScrollPane attributeBrowserScrollPane) throws DicomException {
			super(q,m,treeBrowserScrollPane,attributeBrowserScrollPane);
		}
		/***/
		protected TreeSelectionListener buildTreeSelectionListenerToDoSomethingWithSelectedLevel() {
			return new TreeSelectionListener() {
				public void valueChanged(TreeSelectionEvent tse) {
					TreePath tp = tse.getNewLeadSelectionPath();
					if (tp != null) {
						Object lastPathComponent = tp.getLastPathComponent();
						if (lastPathComponent instanceof QueryTreeRecord) {
							QueryTreeRecord r = (QueryTreeRecord)lastPathComponent;
							setCurrentRemoteQuerySelection(r.getUniqueKeys(),r.getUniqueKey(),r.getAllAttributesReturnedInIdentifier());
							setCurrentRemoteQuerySelectionQueryTreeRecord(r);
						}
					}
				}
			};
		}
	}

	private void loadDicomFileOrDirectory(String dicomFileName) {
		loadDicomFileOrDirectory(dicomFileName,multiPanel,
									referenceImagePanelForImages,
									referenceImagePanelForSpectra);
	}
		
	/**
	 * @param	dicomFileName
	 * @param	imagePanel
	 * @param	referenceImagePanelForImages
	 * @param	referenceImagePanelForSpectra
	 */
	private void loadDicomFileOrDirectory(
			String dicomFileName,JPanel imagePanel,
			JPanel referenceImagePanelForImages,
			JPanel referenceImagePanelForSpectra) {
		// remove currently displayed image, current frame attributes, attribute tree and frame able in case load fails
		// i.e. don't leave stuff from last object loaded hanging around
		// NB. The exception is the DICOMDIR ... if one tries and fails to load
		// a new DICOMDIR, the old contents will not be erased, since otherwise would
		// remove the DICOMDIR when an image load fails ... would be irritating
		// (can't know the new object would be a DICOMDIR unless load and parse succeeds)
		
		//ApplicationEventDispatcher.getApplicationEventDispatcher().removeAllListenersForEventContext(WellKnownContext.MAINPANEL);
		//ApplicationEventDispatcher.getApplicationEventDispatcher().removeAllListenersForEventContext(WellKnownContext.REFERENCEPANEL);
		SingleImagePanel.deconstructAllSingleImagePanelsInContainer(imagePanel);
		SpectraPanel.deconstructAllSpectraPanelsInContainer(imagePanel);
		imagePanel.removeAll();
		imagePanel.repaint();
		SingleImagePanel.deconstructAllSingleImagePanelsInContainer(referenceImagePanelForImages);
		referenceImagePanelForImages.removeAll();
		referenceImagePanelForImages.repaint();
		SingleImagePanel.deconstructAllSingleImagePanelsInContainer(referenceImagePanelForSpectra);
		referenceImagePanelForSpectra.removeAll();
		referenceImagePanelForSpectra.repaint();
		
		imageLocalizerManager.reset();
		spectroscopyLocalizerManager.reset();

		scrollPaneOfCurrentAttributes.setViewportView(null);
		scrollPaneOfCurrentAttributes.repaint();
		attributeTreeScrollPane.setViewportView(null);
		attributeTreeScrollPane.repaint();
		attributeFrameTableScrollPane.setViewportView(null);
		attributeFrameTableScrollPane.repaint();
		structuredReportTreeScrollPane.setViewportView(null);
		structuredReportTreeScrollPane.repaint();
		
		setAttributeListForDatabaseImport(null);
		setCurrentlyDisplayedInstanceFilePath(null);
		
		if (dicomFileName != null) {
			cursorChanger.setWaitCursor();
			try {
System.err.println("Open: "+dicomFileName);
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Reading and parsing DICOM file ..."));
				File file = FileUtilities.getFileFromNameInsensitiveToCaseIfNecessary(dicomFileName);
				dicomFileName = file.getAbsolutePath();		// set to what we actually used, since may be kept around for later imports, etc.
				DicomInputStream i = new DicomInputStream(file);
				AttributeList list = new AttributeList();
//long startTime = System.currentTimeMillis();
				list.read(i);
				i.close();
//long currentTime = System.currentTimeMillis();
//System.err.println("DicomImageViewer.loadDicomFileOrDirectory(): reading AttributeList took = "+(currentTime-startTime)+" ms");
//startTime=currentTime;
				new AttributeTreeBrowser(list,attributeTreeScrollPane);
//currentTime = System.currentTimeMillis();
//System.err.println("DicomImageViewer.loadDicomFileOrDirectory(): making AttributeTreeBrowser took = "+(currentTime-startTime)+" ms");
				// choose type of object based on SOP Class
				// Note that DICOMDIRs don't have SOPClassUID, so check MediaStorageSOPClassUID first
				// then only if not found (e.g. and image with no meta-header, use SOPClassUID from SOP Common Module
				Attribute a = list.get(TagFromName.MediaStorageSOPClassUID);
				String useSOPClassUID = (a != null && a.getVM() == 1) ? a.getStringValues()[0] : null;
				if (useSOPClassUID == null) {
					a = list.get(TagFromName.SOPClassUID);
					useSOPClassUID = (a != null && a.getVM() == 1) ? a.getStringValues()[0] : null;
				}
				
				if (SOPClass.isDirectory(useSOPClassUID)) {
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Building tree from DICOMDIR ..."));
					OurDicomDirectoryBrowser dicomdirBrowser = new OurDicomDirectoryBrowser(list);
					currentDicomDirectory = dicomdirBrowser.getDicomDirectory();	// need access to this later for referenced stuff handling
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new BrowserPaneChangeEvent(WellKnownContext.MAINPANEL,BrowserPaneChangeEvent.DICOMDIR));
				}
				else if (SOPClass.isImageStorage(useSOPClassUID)) {
					//imagePanel.removeAll();
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Building images ..."));
					SourceImage sImg = new SourceImage(list);
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Loading images and attributes ..."));
					
					currentSourceIndex=0;
					currentSourceSortOrder=null;
					getModelOfCurrentAttributesForCurrentFrameBrowser().initializeModelFromAttributeList(list);
					getModelOfCurrentAttributesForCurrentFrameBrowser().selectValuesForDifferentFrame(currentSourceIndex);
					getTableOfCurrentAttributesForCurrentFrameBrowser().setColumnWidths();
					scrollPaneOfCurrentAttributes.setViewportView(getTableOfCurrentAttributesForCurrentFrameBrowser());
					getModelOfCurrentAttributesForAllFramesBrowser().initializeModelFromAttributeList(list);
					getTableOfCurrentAttributesForAllFramesBrowser().setColumnWidths();
					attributeFrameTableScrollPane.setViewportView(getTableOfCurrentAttributesForAllFramesBrowser());
					
					loadMultiPanelWithImage(imagePanel,sImg,list);
					
					referenceImageListMappedToFilenames=getImageListMappedToFilenamesForReferenceOrBackground(list,false);
					displayListOfPossibleReferenceImagesForImages.setListData(referenceImageListMappedToFilenames.keySet().toArray());
					//imagePanel.revalidate();
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new BrowserPaneChangeEvent(WellKnownContext.MAINPANEL,BrowserPaneChangeEvent.IMAGE));
					setAttributeListForDatabaseImport(list);		// warning ... this will keep bulk data hanging around :(
					setCurrentlyDisplayedInstanceFilePath(dicomFileName);
					// set the current selection path in case we want to import or transfer the file we have just loaded
					Vector names = new Vector();
					names.add(dicomFileName);
					setCurrentFilePathSelection(names);
				}
				else if (SOPClass.isSpectroscopy(useSOPClassUID)) {
					//imagePanel.removeAll();
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Building spectra ..."));
					SourceSpectra sSpectra = new SourceSpectra(list);
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Loading spectra and attributes ..."));
					
					currentSourceIndex=0;
					currentSourceSortOrder=null;
					getModelOfCurrentAttributesForCurrentFrameBrowser().initializeModelFromAttributeList(list);
					getModelOfCurrentAttributesForCurrentFrameBrowser().selectValuesForDifferentFrame(currentSourceIndex);
					getTableOfCurrentAttributesForCurrentFrameBrowser().setColumnWidths();
					scrollPaneOfCurrentAttributes.setViewportView(getTableOfCurrentAttributesForCurrentFrameBrowser());
					getModelOfCurrentAttributesForAllFramesBrowser().initializeModelFromAttributeList(list);
					getTableOfCurrentAttributesForAllFramesBrowser().setColumnWidths();
					attributeFrameTableScrollPane.setViewportView(getTableOfCurrentAttributesForAllFramesBrowser());
					
					loadMultiPanelWithSpectra(imagePanel,sSpectra,list);
					
					referenceImageListMappedToFilenames=getImageListMappedToFilenamesForReferenceOrBackground(list,false);
					displayListOfPossibleReferenceImagesForSpectra.setListData(referenceImageListMappedToFilenames.keySet().toArray());
					backgroundImageListMappedToFilenames=getImageListMappedToFilenamesForReferenceOrBackground(list,true);
					displayListOfPossibleBackgroundImagesForSpectra.setListData(backgroundImageListMappedToFilenames.keySet().toArray());
					//imagePanel.revalidate();
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new BrowserPaneChangeEvent(WellKnownContext.MAINPANEL,BrowserPaneChangeEvent.SPECTROSCOPY));
					setAttributeListForDatabaseImport(list);		// warning ... this will keep bulk data hanging around :(
					setCurrentlyDisplayedInstanceFilePath(dicomFileName);
					// set the current selection path in case we want to import or transfer the file we have just loaded
					Vector names = new Vector();
					names.add(dicomFileName);
					setCurrentFilePathSelection(names);
				}
				else if (SOPClass.isStructuredReport(useSOPClassUID) || list.isSRDocument()) {
//System.err.println("DicomImageViewer.loadDicomFileOrDirectory(): SOPClass.isStructuredReport or AttributeList.isSRDocument()");
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Building SR ..."));
					//StructuredReport sSR = new StructuredReport(list);
					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Loading SR and attributes ..."));
					
					new StructuredReportTreeBrowser(list,structuredReportTreeScrollPane);

					ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new BrowserPaneChangeEvent(WellKnownContext.MAINPANEL,BrowserPaneChangeEvent.SR));
					setAttributeListForDatabaseImport(list);		// warning ... this will keep bulk data hanging around :(
					setCurrentlyDisplayedInstanceFilePath(dicomFileName);
					// set the current selection path in case we want to import or transfer the file we have just loaded
					Vector names = new Vector();
					names.add(dicomFileName);
					setCurrentFilePathSelection(names);
				}
				else if (SOPClass.isNonImageStorage(useSOPClassUID)) {
					throw new DicomException("unsupported storage SOP Class "+useSOPClassUID);
				}
				else {
					throw new DicomException("unsupported SOP Class "+useSOPClassUID);
				}
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			} catch (Exception e) {
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(e.toString()));
				//System.err.println(e);
				e.printStackTrace(System.err);
			}
			// make label really wide, else doesn't completely repaint on status update
			cursorChanger.restoreCursor();
		}
	}

	/**
	 * @param	imagePanel
	 * @param	referenceImagePanelForImages
	 * @param	referenceImagePanelForSpectra
	 */
	private void callFileChooserThenLoadDicomFileOrDirectory(
		JPanel imagePanel,
		JPanel referenceImagePanelForImages,
		JPanel referenceImagePanelForSpectra) {

		String dicomFileName = null;
		{
			SafeFileChooser chooser = new SafeFileChooser(lastDirectoryPath);
			if (chooser.showOpenDialog(getContentPane()) == JFileChooser.APPROVE_OPTION) {
				dicomFileName=chooser.getSelectedFile().getAbsolutePath();
				lastDirectoryPath=chooser.getCurrentDirectory().getAbsolutePath();
			}
		}
		loadDicomFileOrDirectory(dicomFileName,imagePanel,
			referenceImagePanelForImages,
			referenceImagePanelForSpectra);
	}
	
	/**
	 * @param	dicomFileName
	 * @param	ae
	 * @param	hostname
	 * @param	port
	 * @param	calledAETitle
	 * @param	callingAETitle
	 * @param	affectedSOPClass
	 * @param	affectedSOPInstance
	 */
	private void sendDicomFileOverDicomNetwork(String dicomFileName,String ae,String hostname,int port,
			String calledAETitle,String callingAETitle,String affectedSOPClass,String affectedSOPInstance) {
		if (dicomFileName != null) {
			cursorChanger.setWaitCursor();
			try {
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Sending image "+dicomFileName+" to "+ae+" ..."));
				int storageSCUDebugLevel = networkApplicationProperties.getStorageSCUDebugLevel();
				int storageSCUCompressionLevel = networkApplicationProperties.getStorageSCUCompressionLevel();
				new StorageSOPClassSCU(hostname,port,calledAETitle,callingAETitle,dicomFileName,affectedSOPClass,affectedSOPInstance,
					storageSCUCompressionLevel,storageSCUDebugLevel);
			}
			catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}

	/***/
	private class DicomFileOrDirectoryLoadActionListener implements ActionListener {
		/***/
		JPanel imagePanel;
		/***/
		JPanel referenceImagePanelForImages;
		/***/
		JPanel referenceImagePanelForSpectra;
		
		/**
		 * @param	imagePanel
		 * @param	referenceImagePanelForImages
		 * @param	referenceImagePanelForSpectra
		 */
		public DicomFileOrDirectoryLoadActionListener(JPanel imagePanel,
				JPanel referenceImagePanelForImages,
				JPanel referenceImagePanelForSpectra) {
			this.imagePanel=imagePanel;
			this.referenceImagePanelForImages=referenceImagePanelForImages;
			this.referenceImagePanelForSpectra=referenceImagePanelForSpectra;
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			//new Thread() { public void run() {
			callFileChooserThenLoadDicomFileOrDirectory(imagePanel,
				referenceImagePanelForImages,
				referenceImagePanelForSpectra);
			//} }.start();
		}
	}

	/***/

	/***/
	private TreeMap backgroundImageListMappedToFilenames = null;
	
	/***/
	private TreeMap referenceImageListMappedToFilenames = null;
	
	/***/
	private final TreeMap getImageListMappedToFilenamesForReferenceOrBackground(AttributeList referencedFromList,boolean requireSameImageOrientationPatient) {
		TreeMap imageListMappedToFilenames = new TreeMap();	// of String descriptions; each possible only once, sorted lexicographically; mapped to String file name
		String frameOfReferenceUID=Attribute.getSingleStringValueOrNull(referencedFromList,TagFromName.FrameOfReferenceUID);
		double[] wantedImageOrientationPatient=GeometryOfVolumeFromAttributeList.getImageOrientationPatientFromAttributeList(referencedFromList);
		if (frameOfReferenceUID != null) {
			if (databaseInformationModel != null) {
				try {
					//ArrayList values = databaseInformationModel.findSelectedAttributeValueForAllRecordsForThisInformationEntityWithMatchingParent(
					ArrayList returnedRecords = databaseInformationModel.findAllAttributeValuesForAllRecordsForThisInformationEntityWithMatchingParent(
							InformationEntity.INSTANCE,
							InformationEntity.SERIES,
							"FrameOfReferenceUID",
							frameOfReferenceUID);
					if (returnedRecords != null && returnedRecords.size() > 0) {
						for (int i=0; i<returnedRecords.size(); ++i) {
							String value = null;
							Map returnedAttributes = (Map)(returnedRecords.get(i));
							if (returnedAttributes != null) {
								String description = DescriptionFactory.makeImageDescription(returnedAttributes);
								String sopInstanceUID = (String)(returnedAttributes.get("SOPINSTANCEUID"));
								String sopClassUID = (String)(returnedAttributes.get("SOPCLASSUID"));
								double[] imageOrientationPatient = null;
								{
									String s = (String)(returnedAttributes.get("IMAGEORIENTATIONPATIENT"));
									if (s != null) {
										imageOrientationPatient=FloatFormatter.fromString(s,6,'\\');
									}
								}
								if (!requireSameImageOrientationPatient
								 || (wantedImageOrientationPatient != null && wantedImageOrientationPatient.length == 6
								  && imageOrientationPatient != null && imageOrientationPatient.length == 6
								  && ArrayCopyUtilities.arraysAreEqual(wantedImageOrientationPatient,imageOrientationPatient))) {
									String filename = (String)(returnedAttributes.get(
										databaseInformationModel.getLocalFileNameColumnName(InformationEntity.INSTANCE)));
									// only images and no duplicates ...
									if (filename != null && sopClassUID != null && SOPClass.isImageStorage(sopClassUID)
									 && !imageListMappedToFilenames.containsKey(description)) {
										imageListMappedToFilenames.put(description,filename);
//System.err.println("Potential reference in same Frame of Reference: "+description+" "+filename);
									}
								}
							}
						}
					}
				}
				catch (DicomException e) {
					e.printStackTrace(System.err);
				}
			}
			// NB. since always checks for description key, will not use a reference from the DICOMDIR
			// if there is already one from the database ...
			if (currentDicomDirectory != null) {
				Vector attributeLists = currentDicomDirectory.findAllImagesForFrameOfReference(frameOfReferenceUID);	// only images
				if (attributeLists != null) {
					for (int j=0; j<attributeLists.size(); ++j) {
						AttributeList referencedList = (AttributeList)(attributeLists.get(j));
//System.err.println("Same Frame Of Reference:");
//System.err.println(referencedList);
						if (referencedList != null) {
							String description = DescriptionFactory.makeImageDescription(referencedList);
							String sopInstanceUID = Attribute.getSingleStringValueOrNull(referencedList,TagFromName.ReferencedSOPInstanceUIDInFile);
							double[] imageOrientationPatient=Attribute.getDoubleValues(referencedList,TagFromName.ImageOrientationPatient);
							if (sopInstanceUID != null) {
								String filename = null;
								try {
									// get name which has parent path all fixed up already ....
									filename = currentDicomDirectory.getReferencedFileNameForSOPInstanceUID(sopInstanceUID);
								}
								catch (DicomException e) {
								}
								if (!requireSameImageOrientationPatient
								 || (wantedImageOrientationPatient != null && wantedImageOrientationPatient.length == 6
								  && imageOrientationPatient != null && imageOrientationPatient.length == 6
								  && ArrayCopyUtilities.arraysAreEqual(wantedImageOrientationPatient,imageOrientationPatient))) {
									// no duplicates ...
									if (filename != null && !imageListMappedToFilenames.containsKey(description)) {
										imageListMappedToFilenames.put(description,filename);
//System.err.println("Potential reference in same Frame of Reference: "+description+" "+filename);
									}
								}
							}
						}
					}
				}
			}
		}
		return imageListMappedToFilenames;
	}
	
	/***/
	private JPanel referenceImagePanelForImages = null;

	/***/
	private JPanel referenceImagePanelForSpectra = null;

	/***/
	private final class OurReferenceListSelectionListener implements ListSelectionListener {
		/***/
		private String lastSelectedDicomFileName = null;
		/***/
		private JPanel referenceImagePanel;
		/***/
		private boolean spectroscopy;
		
		OurReferenceListSelectionListener(JPanel referenceImagePanel,boolean spectroscopy) {
			super();
			this.referenceImagePanel=referenceImagePanel;
			this.spectroscopy=spectroscopy;
		}
		
		/***/
		public void valueChanged(ListSelectionEvent e) {
//System.err.println("The class of the ListSelectionEvent source is " + e.getSource().getClass().getName());
			JList list = (JList)(e.getSource());
//System.err.println("Selection event is "+e);
			if (list.isSelectionEmpty()) {
				// such as when list has been reloaded ...
				lastSelectedDicomFileName=null;		// Fixes [bugs.mrmf] (000070) Localizer/spectra background sometimes doesn't load on selection, or reselection
			}
			else {
//System.err.println("List selection is not empty");
				String key = (String)list.getSelectedValue();
				if (key != null) {
//System.err.println("List selection key is not null = "+key);
					String dicomFileName = (String)referenceImageListMappedToFilenames.get(key);
//System.err.println("List selection dicomFileName = "+dicomFileName);
					// collapse redundant duplicate events
					if (dicomFileName != null && (lastSelectedDicomFileName == null || !dicomFileName.equals(lastSelectedDicomFileName))) {
//System.err.println("New selection "+key+" "+dicomFileName);
						lastSelectedDicomFileName=dicomFileName;
						loadReferenceImagePanel(dicomFileName,referenceImagePanel,spectroscopy);
					}
				}
			}
		}
	}
	

	/***/
	private final class OurBackgroundListSelectionListener implements ListSelectionListener {
		/***/
		private String lastSelectedDicomFileName = null;
		
		OurBackgroundListSelectionListener() {
			super();
		}
		
		/***/
		public void valueChanged(ListSelectionEvent e) {
//System.err.println("The class of the ListSelectionEvent source is " + e.getSource().getClass().getName());
			JList list = (JList)(e.getSource());

			if (list.isSelectionEmpty()) {
				// such as when list has been reloaded ...
				lastSelectedDicomFileName=null;		// Fixes [bugs.mrmf] (000070) Localizer/spectra background sometimes doesn't load on selection, or reselection
			}
			else {
				String key = (String)list.getSelectedValue();
				if (key != null) {
					String dicomFileName = (String)backgroundImageListMappedToFilenames.get(key);
					// collapse redundant duplicate events
					if (dicomFileName != null && (lastSelectedDicomFileName == null || !dicomFileName.equals(lastSelectedDicomFileName))) {
//System.err.println("New selection "+key+" "+dicomFileName);
						lastSelectedDicomFileName=dicomFileName;
						loadBackgroundImageForSpectra(dicomFileName);
					}
				}
			}
		}
	}
	
	/***/
	private String showInputDialogToSelectNetworkTargetByLocalApplicationEntityName(String question,String buttonText,String defaultSelection) {
//System.err.println("DicomImageViewer.showInputDialogToSelectNetworkTargetByLocalApplicationEntityName()");
		String ae = defaultSelection;
		if (networkApplicationProperties != null) {
//System.err.println("DicomImageViewer.showInputDialogToSelectNetworkTargetByLocalApplicationEntityName(): have networkApplicationProperties");
			Set localNamesOfRemoteAEs = networkApplicationInformation.getListOfLocalNamesOfApplicationEntities();
			if (localNamesOfRemoteAEs != null) {
//System.err.println("DicomImageViewer.showInputDialogToSelectNetworkTargetByLocalApplicationEntityName(): got localNamesOfRemoteAEs");
				String sta[] = new String[localNamesOfRemoteAEs.size()];
				int i=0;
				Iterator it = localNamesOfRemoteAEs.iterator();
				while (it.hasNext()) {
					sta[i++]=(String)(it.next());
				}
				ae = (String)JOptionPane.showInputDialog(getContentPane(),question,buttonText,JOptionPane.QUESTION_MESSAGE,null,sta,ae);
			}
		}
		return ae;
	}

	/***/
	private class QuerySelectActionListener implements ActionListener {
		/***/
		JTabbedPane browserPane;
		/***/
		int tabNumberOfRemoteInBrowserPane;
		/**
		 * @param	treeScrollPane
		 * @param	scrollPaneOfCurrentAttributes
		 * @param	browserPane
		 * @param	tabNumberOfRemoteInBrowserPane
		 */
		public QuerySelectActionListener(JTabbedPane browserPane,int tabNumberOfRemoteInBrowserPane) {
			this.browserPane=browserPane;
			this.tabNumberOfRemoteInBrowserPane=tabNumberOfRemoteInBrowserPane;
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			Properties properties = getProperties();
			String ae = properties.getProperty(propertyName_DicomCurrentlySelectedQueryTargetAE);
			ae = showInputDialogToSelectNetworkTargetByLocalApplicationEntityName("Select remote system","Query ...",ae);
			queryTreeScrollPane.setViewportView(null);
			scrollPaneOfCurrentAttributes.setViewportView(null);
			if (ae != null) setCurrentRemoteQueryInformationModel(ae,browserPane,tabNumberOfRemoteInBrowserPane);
		}
	}
	
	/***/
	private class QueryFilterActionListener implements ActionListener {
		/***/
		JTabbedPane browserPane;
		/***/
		int tabNumberOfRemoteInBrowserPane;
		/**
		 * @param	browserPane
		 * @param	tabNumberOfRemoteInBrowserPane
		 */
		public QueryFilterActionListener(JTabbedPane browserPane,int tabNumberOfRemoteInBrowserPane) {
			this.browserPane=browserPane;
			this.tabNumberOfRemoteInBrowserPane=tabNumberOfRemoteInBrowserPane;
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
//System.err.println("QueryFilterActionListener.actionPerformed()");
			queryTreeScrollPane.setViewportView(new FilterPanel(getCurrentRemoteQueryFilter()));
			//scrollPaneOfCurrentAttributes.setViewportView(null);
		}
	}
	
	private void performRetrieve(AttributeList uniqueKeys,String selectionLevel,String retrieveAE) {
		try {
			AttributeList identifier = new AttributeList();
			if (uniqueKeys != null) {
				identifier.putAll(uniqueKeys);
				{ AttributeTag t = TagFromName.QueryRetrieveLevel; Attribute a = new CodeStringAttribute(t);
					a.addValue(selectionLevel); identifier.put(t,a); }
				QueryInformationModel queryInformationModel = getCurrentRemoteQueryInformationModel();
				queryInformationModel.performHierarchicalMoveFrom(identifier,retrieveAE);
			}
			// else do nothing, since no unique key to specify what to retrieve
		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
	
	/***/
	private class QueryRetrieveActionListener implements ActionListener {
		/**
		 */
		public QueryRetrieveActionListener() {
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			if (getCurrentRemoteQuerySelectionLevel() == null) {	// they have selected the root of the tree
				QueryTreeRecord parent = getCurrentRemoteQuerySelectionQueryTreeRecord();
				if (parent != null) {
System.err.println("Retrieve: everything from "+getCurrentRemoteQuerySelectionRetrieveAE());
					Enumeration children = parent.children();
					if (children != null) {
						while (children.hasMoreElements()) {
							QueryTreeRecord r = (QueryTreeRecord)(children.nextElement());
							if (r != null) {
								setCurrentRemoteQuerySelection(r.getUniqueKeys(),r.getUniqueKey(),r.getAllAttributesReturnedInIdentifier());
System.err.println("Retrieve: "+getCurrentRemoteQuerySelectionLevel()+" "+getCurrentRemoteQuerySelectionUniqueKey().getSingleStringValueOrEmptyString()+" from "+getCurrentRemoteQuerySelectionRetrieveAE());
								performRetrieve(getCurrentRemoteQuerySelectionUniqueKeys(),getCurrentRemoteQuerySelectionLevel(),getCurrentRemoteQuerySelectionRetrieveAE());
							}
						}
					}
System.err.println("Retrieve done");
					setCurrentRemoteQuerySelection(null,null,null);
				}
			}
			else {
//System.err.println("DicomImageViewer.QueryRetrieveActionListener.actionPerformed(): "+getCurrentRemoteQuerySelectionUniqueKeys()+" from="+getCurrentRemoteQuerySelectionRetrieveAE()+" level="+getCurrentRemoteQuerySelectionLevel());
System.err.println("Retrieve: "+getCurrentRemoteQuerySelectionLevel()+" "+getCurrentRemoteQuerySelectionUniqueKey().getSingleStringValueOrEmptyString()+" from "+getCurrentRemoteQuerySelectionRetrieveAE());
				performRetrieve(getCurrentRemoteQuerySelectionUniqueKeys(),getCurrentRemoteQuerySelectionLevel(),getCurrentRemoteQuerySelectionRetrieveAE());
System.err.println("Retrieve done");
			}
			cursorChanger.restoreCursor();
		}
	}
	
	/***/
	private class QueryRefreshActionListener implements ActionListener {
		/**
		 */
		public QueryRefreshActionListener() {
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
				queryTreeScrollPane.setViewportView(null);
				scrollPaneOfCurrentAttributes.setViewportView(null);
				QueryInformationModel queryInformationModel = getCurrentRemoteQueryInformationModel();
				if (queryInformationModel != null) {
					// make sure that Specific Character Set is updated to reflect any text values with funky characters that may have been entered in the filter ...
					AttributeList filter = getCurrentRemoteQueryFilter();
					filter.insertSuitableSpecificCharacterSetForAllStringValues();
					QueryTreeModel treeModel = queryInformationModel.performHierarchicalQuery(filter);
					new OurQueryTreeBrowser(queryInformationModel,treeModel,queryTreeScrollPane,scrollPaneOfCurrentAttributes);
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			cursorChanger.restoreCursor();
		}
	}
	
	// very similar to code in DicomCleaner and DoseUtility apart from logging and progress bar ... should refactor :(
	protected void purgeFilesAndDatabaseInformation(DatabaseTreeRecord[] databaseSelections) throws DicomException, IOException {
		if (databaseSelections != null) {
			for (DatabaseTreeRecord databaseSelection : databaseSelections) {
				purgeFilesAndDatabaseInformation(databaseSelection);
			}
		}
	}
					
	protected void purgeFilesAndDatabaseInformation(DatabaseTreeRecord databaseSelection) throws DicomException, IOException {
//System.err.println("DicomImageViewer.purgeFilesAndDatabaseInformation(): "+databaseSelection);
		if (databaseSelection != null) {
			InformationEntity ie = databaseSelection.getInformationEntity();
//System.err.println("DicomImageViewer.purgeFilesAndDatabaseInformation(): ie = "+ie);
			if (ie == null /* the root of the tree, i.e., everything */ || !ie.equals(InformationEntity.INSTANCE)) {
				// Do it one study at a time, in the order in which the patients and studies are sorted in the tree
				Enumeration children = databaseSelection.children();
				if (children != null) {
					while (children.hasMoreElements()) {
						DatabaseTreeRecord child = (DatabaseTreeRecord)(children.nextElement());
						if (child != null) {
							purgeFilesAndDatabaseInformation(child);
						}
					}
				}
				// AFTER we have processed all the children, if any, we can delete ourselves, unless we are the root
				if (ie != null) {
//System.err.println("DicomImageViewer.purgeFilesAndDatabaseInformation(): removeFromParent having recursed over children "+databaseSelection);
					databaseSelection.removeFromParent();
				}
			}
			else {
				// Instance level ... may need to delete files
				String fileName = databaseSelection.getLocalFileNameValue();
				String fileReferenceType = databaseSelection.getLocalFileReferenceTypeValue();
//System.err.println("DicomImageViewer.purgeFilesAndDatabaseInformation(): fileReferenceType = "+fileReferenceType+" for file "+fileName);
				if (fileReferenceType != null && fileReferenceType.equals(DatabaseInformationModel.FILE_COPIED)) {
//System.err.println("DicomImageViewer.purgeFilesAndDatabaseInformation(): deleting fileName "+fileName);
					try {
						if (!new File(fileName).delete()) {
							System.err.println("Failed to delete local copy of file "+fileName);
						}
					}
					catch (Exception e) {
						e.printStackTrace(System.err);
					}
				}
//System.err.println("DicomImageViewer.purgeFilesAndDatabaseInformation(): removeFromParent instance level "+databaseSelection);
				databaseSelection.removeFromParent();
			}
		}
	}

	protected class DatabasePurgeWorker implements Runnable {
		DatabaseTreeRecord[] databaseSelections;
		
		DatabasePurgeWorker(DatabaseTreeRecord[] databaseSelections) {
			this.databaseSelections=databaseSelections;
		}

		public void run() {
			cursorChanger.setWaitCursor();
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Purging started"));
			try {
				purgeFilesAndDatabaseInformation(databaseSelections);
			} catch (Exception e) {
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Purging failed: "+e));
				e.printStackTrace(System.err);
			}
			try {
				new OurDatabaseTreeBrowser();
			} catch (Exception e) {
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Refresh source database browser failed: "+e));
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done purging"));
			cursorChanger.restoreCursor();
		}
	}

	private class DatabasePurgeActionListener implements ActionListener {
		public void actionPerformed(ActionEvent event) {
			try {
				new Thread(new DatabasePurgeWorker(getCurrentDatabaseTreeRecordSelections())).start();

			} catch (Exception e) {
				ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Purging failed: "+e));
				e.printStackTrace(System.err);
			}
		}
	}
		
	/***/
	private class DatabaseRefreshActionListener implements ActionListener {
		/**
		 */
		public DatabaseRefreshActionListener() {
		}
		
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			try {
				new OurDatabaseTreeBrowser();
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}
	}
	
	/***/
	private class DatabaseImportFromFilesActionListener implements ActionListener {
		/***/
		private DatabaseMediaImporter importer;
		/**
		 */
		public DatabaseImportFromFilesActionListener() {
			this.importer = new DatabaseMediaImporter(null/*initial path*/,savedImagesFolder,storedFilePathStrategy,databaseInformationModel,/*null*/new OurMessageLogger());
		}

		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
//System.err.println("DicomImageViewer.DatabaseImportFromFilesActionListener.actionPerformed(): caling importer.choosePathAndImportDicomFiles()");
				importer.choosePathAndImportDicomFiles(DicomImageViewer.this.getContentPane());
				new OurDatabaseTreeBrowser();
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}
	
	/**
	 * @param	list
	 * @param	fileName
	 * @exception	IOException
	 * @exception	DicomException
	 */
	/*
	private void copyFileAndImportToDatabase(AttributeList list,String fileName) throws DicomException, IOException {

		String sopInstanceUID = Attribute.getSingleStringValueOrNull(list,TagFromName.SOPInstanceUID);
		if (sopInstanceUID == null) {
			throw new DicomException("Cannot get SOP Instance UID to make file name for local copy when inserting into database");
		}
		String localCopyFileName=storedFilePathStrategy.makeReliableStoredFilePathWithFoldersCreated(savedImagesFolder,sopInstanceUID).getPath();
//System.err.println("DicomImageViewer.copyFileAndImportToDatabase(): uid = "+sopInstanceUID+" path ="+localCopyFileName);
		if (fileName.equals(localCopyFileName)) {
System.err.println("DicomImageViewer.copyFileAndImportToDatabase(): input and output filenames identical - presumably copying from our own database back into our own database, so doing nothing");
		}
		else {
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Copying object ..."));
			CopyStream.copy(new BufferedInputStream(new FileInputStream(fileName)),new BufferedOutputStream(new FileOutputStream(localCopyFileName)));
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Inserting into database ..."));
			databaseInformationModel.insertObject(list,localCopyFileName,DatabaseInformationModel.FILE_COPIED);
		}
	}

	/***/
	private class ImportCurrentlyDisplayedInstanceToDatabaseActionListener implements ActionListener {
		/**
		 */
		public ImportCurrentlyDisplayedInstanceToDatabaseActionListener() {
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
				//copyFileAndImportToDatabase(getAttributeListForDatabaseImport(),getCurrentlyDisplayedInstanceFilePath());
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}
	
	/***/
/*
	public class ImportFromSelectionToDatabaseActionListener implements ActionListener {
		
		public ImportFromSelectionToDatabaseActionListener() {
		}
		
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			Vector paths = getCurrentFilePathSelections();
			if (paths != null) {
				for (int j=0; j< paths.size(); ++j) {
					String dicomFileName = (String)(paths.get(j));
					if (dicomFileName != null) {
						try {
							File file = FileUtilities.getFileFromNameInsensitiveToCaseIfNecessary(dicomFileName);
							DicomInputStream i = new DicomInputStream(file);
							AttributeList list = new AttributeList();
							list.read(i);
							i.close();
							//databaseInformationModel.insertObject(list,dicomFileName);
							//copyFileAndImportToDatabase(list,file.getAbsolutePath());
						} catch (Exception e) {
							e.printStackTrace(System.err);
						}
					}
				}
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}
*/
	/***/
	private class NetworkSendCurrentSelectionActionListener implements ActionListener {
		/**
		 */
		public NetworkSendCurrentSelectionActionListener() {
		}

		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			Vector paths = getCurrentFilePathSelections();
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): paths="+paths);
			if (paths != null && paths.size() > 0) {
				//boolean coerce = JOptionPane.showConfirmDialog(null,"Change identifiers during send ?  ","Send ...",
				//	JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
				//if (coerce) {
				//	CoercionModel coercionModel = new CoercionModel(paths);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): CoercionModel="+coercionModel);
				//}
			
				Properties properties = getProperties();
				String ae = properties.getProperty(propertyName_DicomCurrentlySelectedStorageTargetAE);
				ae = showInputDialogToSelectNetworkTargetByLocalApplicationEntityName("Select destination","Send ...",ae);
				if (ae != null && networkApplicationProperties != null) {
					try {
						String                   callingAETitle = networkApplicationProperties.getCallingAETitle();
						String                    calledAETitle = networkApplicationInformation.getApplicationEntityTitleFromLocalName(ae);
						PresentationAddress presentationAddress = networkApplicationInformation.getApplicationEntityMap().getPresentationAddress(calledAETitle);
						String                         hostname = presentationAddress.getHostname();
						int                                port = presentationAddress.getPort();
						
						String affectedSOPClass = null;
						String affectedSOPInstance = null;
				
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): ae="+ae);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): hostname="+hostname);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): port="+port);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): calledAETitle="+calledAETitle);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): callingAETitle="+callingAETitle);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): affectedSOPClass="+affectedSOPClass);
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): affectedSOPInstance="+affectedSOPInstance);

						for (int j=0; j< paths.size(); ++j) {
							String dicomFileName = (String)(paths.get(j));
							if (dicomFileName != null) {
								try {
//System.err.println("NetworkSendCurrentSelectionActionListener.actionPerformed(): dicomFileName="+dicomFileName);
System.err.println("Send: "+dicomFileName);
									File file = FileUtilities.getFileFromNameInsensitiveToCaseIfNecessary(dicomFileName);
									sendDicomFileOverDicomNetwork(file.getAbsolutePath(),ae,hostname,port,calledAETitle,callingAETitle,
										affectedSOPClass,affectedSOPInstance);
								} catch (Exception e) {
									e.printStackTrace(System.err);
								}
							}
						}
					}
					catch (Exception e) {		// if an AE's property has no value
						e.printStackTrace(System.err);
					}
				}
				// else user cancelled operation in JOptionPane.showInputDialog() so gracefully do nothing
			}
		}
	}
	
	/***/
	private class SaveCurrentlyDisplayedImageToXMLActionListener implements ActionListener {
		/**
		 */
		public SaveCurrentlyDisplayedImageToXMLActionListener() {
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
				SafeFileChooser chooser = new SafeFileChooser(lastDirectoryPath);
				if (chooser.showSaveDialog(getContentPane()) == JFileChooser.APPROVE_OPTION) {
					String xmlFileName=chooser.getSelectedFile().getAbsolutePath();
					lastDirectoryPath=chooser.getCurrentDirectory().getAbsolutePath();
					AttributeList list = getAttributeListForDatabaseImport();
					new XMLRepresentationOfDicomObjectFactory().createDocumentAndWriteIt(list,new BufferedOutputStream(new FileOutputStream(xmlFileName)));
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}
	
		
	/***/
	private class SaveCurrentlyDisplayedStructuredReportToXMLActionListener implements ActionListener {
		/**
			*/
		public SaveCurrentlyDisplayedStructuredReportToXMLActionListener() {
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
				SafeFileChooser chooser = new SafeFileChooser(lastDirectoryPath);
				if (chooser.showSaveDialog(DicomImageViewer.this.getContentPane()) == JFileChooser.APPROVE_OPTION) {
					String xmlFileName=chooser.getSelectedFile().getAbsolutePath();
					lastDirectoryPath=chooser.getCurrentDirectory().getAbsolutePath();
					AttributeList list = getAttributeListForDatabaseImport();
					new XMLRepresentationOfStructuredReportObjectFactory().createDocumentAndWriteIt(list,new BufferedOutputStream(new FileOutputStream(xmlFileName)));
				}
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}

	/***/
	private class ValidateCurrentlyDisplayedImageActionListener implements ActionListener {
		/***/
		DicomInstanceValidator validator;

		/**
		 */
		public ValidateCurrentlyDisplayedImageActionListener() {
			validator=null;
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
				AttributeList list = getAttributeListForDatabaseImport();
				if (validator == null) {
					// lazy instantiation to speed up start up
					validator = new DicomInstanceValidator();
				}
				String outputString = validator == null ? "Could not instantiate a validator\n" : validator.validate(list);
				JTextArea outputTextArea = new JTextArea(outputString);
				JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
				JDialog outputDialog = new JDialog();
				outputDialog.setSize(512,384);
				outputDialog.setTitle("Validation of "+getCurrentFilePathSelection());
				outputDialog.getContentPane().add(outputScrollPane);
				outputDialog.setVisible(true);

			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}
		
	/***/
	private class ValidateCurrentlyDisplayedStructuredReportActionListener implements ActionListener {
		/***/
		DicomSRValidator validator;
			
		/**
		 */
		public ValidateCurrentlyDisplayedStructuredReportActionListener() {
			validator=null;
		}
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
			cursorChanger.setWaitCursor();
			try {
				AttributeList list = getAttributeListForDatabaseImport();
				if (validator == null) {
					// lazy instantiation to speed up start up
					validator = new DicomSRValidator();
				}
				String outputString = validator == null ? "Could not instantiate a validator\n" : validator.validate(list);
				JTextArea outputTextArea = new JTextArea(outputString);
				JScrollPane outputScrollPane = new JScrollPane(outputTextArea);
				JDialog outputDialog = new JDialog();
				outputDialog.setSize(512,384);
				outputDialog.setTitle("Validation of "+getCurrentFilePathSelection());
				outputDialog.getContentPane().add(outputScrollPane);
				outputDialog.setVisible(true);
					
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
			ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent("Done.                                                   "));
			cursorChanger.restoreCursor();
		}
	}
		
	/***/
	private class OurReceivedObjectHandler extends ReceivedObjectHandler {
		//private DatabaseInformationModel databaseInformationModel;
		
		//OurReceivedObjectHandler(DatabaseInformationModel databaseInformationModel) {
		//	this.databaseInformationModel=databaseInformationModel;
		//}
		
		/**
		 * @param	dicomFileName
		 * @param	transferSyntax
		 * @param	callingAETitle
		 * @exception	IOException
		 * @exception	DicomException
		 * @exception	DicomNetworkException
		 */
		public void sendReceivedObjectIndication(String dicomFileName,String transferSyntax,String callingAETitle)
				throws DicomNetworkException, DicomException, IOException {
//System.err.println("DicomImageViewer.OurReceivedObjectHandler.sendReceivedObjectIndication() dicomFileName: "+dicomFileName);
			if (dicomFileName != null) {
System.err.println("Received: "+dicomFileName+" from "+callingAETitle+" in "+transferSyntax);
				try {
//long startTime = System.currentTimeMillis();
					// no need for case insensitive check here ... was locally created
					FileInputStream fis = new FileInputStream(dicomFileName);
					DicomInputStream i = new DicomInputStream(new BufferedInputStream(fis));
					AttributeList list = new AttributeList();
					list.read(i,TagFromName.PixelData);
					i.close();
					fis.close();
//long afterReadTime = System.currentTimeMillis();
//System.err.println("Received: time to read list "+(afterReadTime-startTime)+" ms");
					databaseInformationModel.insertObject(list,dicomFileName,DatabaseInformationModel.FILE_COPIED);
//long afterInsertTime = System.currentTimeMillis();
//System.err.println("Received: time to insert in database "+(afterInsertTime-afterReadTime)+" ms");
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}

		}
	}
	
	private ButtonGroup attributeTreeSortOrderButtons = new ButtonGroup();

	/***/
	private class SortAttributesActionListener implements ActionListener {
	
		static final String ByName = "NAME";
		static final String ByNumber = "NUMBER";

		/**
		 */
		public SortAttributesActionListener() {
		}
		
		/**
		 * @param	event
		 */
		public void actionPerformed(ActionEvent event) {
//System.err.println("SortAttributesActionListener.SortAttributesActionListener.actionPerformed()");
			String choice = attributeTreeSortOrderButtons.getSelection().getActionCommand();
//System.err.println("SortAttributesActionListener.SortAttributesActionListener.actionPerformed(): choice="+choice);
			AttributeTreeBrowser.setSortByName(attributeTreeScrollPane,choice != null && choice.equals(ByName));

		}
	}
		
	public void osxFileHandler(String fileName) {
//System.err.println("DicomImageViewer.osxFileHandler(): fileName = "+fileName);
		lastDirectoryPath = new File(fileName).getParent();		// needed, since otherwise can't load children inside DICOMDIR
//System.err.println("DicomImageViewer.osxFileHandler(): setting lastDirectoryPath = "+lastDirectoryPath);
		loadDicomFileOrDirectory(fileName);
	}

	// Based on Apple's MyApp.java example supplied with OSXAdapter ...
	// Generic registration with the Mac OS X application menu
	// Checks the platform, then attempts to register with the Apple EAWT
	// See OSXAdapter.java to see how this is done without directly referencing any Apple APIs
	public void registerForMacOSXEvents() {
		if (System.getProperty("os.name").toLowerCase(java.util.Locale.US).startsWith("mac os x")) {
//System.err.println("DicomImageViewer.registerForMacOSXEvents(): on MacOSX");
			try {
				// Generate and register the OSXAdapter, passing it a hash of all the methods we wish to
				// use as delegates for various com.apple.eawt.ApplicationListener methods
				OSXAdapter.setQuitHandler(this, getClass().getDeclaredMethod("quit", (Class[])null));		// need this, else won't quit from X or Cmd-Q any more, once any events registered
				//OSXAdapter.setAboutHandler(this, getClass().getDeclaredMethod("about", (Class[])null));
				//OSXAdapter.setPreferencesHandler(this, getClass().getDeclaredMethod("preferences", (Class[])null));
				OSXAdapter.setFileHandler(this, getClass().getDeclaredMethod("osxFileHandler", new Class[] { String.class }));
			} catch (NoSuchMethodException e) {
				// trap it, since we don't want to fail just because we cannot register events
				e.printStackTrace();
			}
		}
	}

	/**
	 * @param	title
	 * @param	dicomFileName
	 */
	private void doCommonConstructorStuff(String title,String dicomFileName) {
		registerForMacOSXEvents();

		mainPanelFrameSortOrderChangeListener = new OurFrameSortOrderChangeListener(WellKnownContext.MAINPANEL);
		mainPanelFrameSelectionChangeListener = new OurFrameSelectionChangeListener(WellKnownContext.MAINPANEL);

		// No frame selection or sort order listener required for reference panel
		
		addKeyListener(this);		// for screen snapshot
		addMouseListener(this);		// for screen snapshot (allows us to grab keyboard focus by moving mouse out and back into app)

		{
			spectroscopyLocalizerManager = new SpectroscopyLocalizerManager();
			spectroscopyLocalizerManager.setReferenceSourceImageSelectionContext(WellKnownContext.REFERENCEPANEL);
			spectroscopyLocalizerManager.setReferenceImageFrameSelectionContext(WellKnownContext.REFERENCEPANEL);
			spectroscopyLocalizerManager.setReferenceImageFrameSortOrderContext(WellKnownContext.REFERENCEPANEL);
			spectroscopyLocalizerManager.setSourceSpectrumSelectionContext(WellKnownContext.MAINPANEL);
			spectroscopyLocalizerManager.setSpectrumFrameSelectionContext(WellKnownContext.MAINPANEL);
			spectroscopyLocalizerManager.setSpectrumFrameSortOrderContext(WellKnownContext.MAINPANEL);
			
			imageLocalizerManager = new ImageLocalizerManager();
			imageLocalizerManager.setReferenceSourceImageSelectionContext(WellKnownContext.REFERENCEPANEL);
			imageLocalizerManager.setReferenceImageFrameSelectionContext(WellKnownContext.REFERENCEPANEL);
			imageLocalizerManager.setReferenceImageFrameSortOrderContext(WellKnownContext.REFERENCEPANEL);
			imageLocalizerManager.setMainSourceImageSelectionContext(WellKnownContext.MAINPANEL);
			imageLocalizerManager.setMainImageFrameSelectionContext(WellKnownContext.MAINPANEL);
			imageLocalizerManager.setMainImageFrameSortOrderContext(WellKnownContext.MAINPANEL);
		}
		
		Properties properties = getProperties();
//System.err.println("properties="+properties);

		System.out.println(properties);
		
		databaseApplicationProperties = new DatabaseApplicationProperties(properties);
		
		savedImagesFolder = null;
		
		if (databaseApplicationProperties != null) {
		
			// Make sure there is a folder to store received and imported images ...
		
			try {
				savedImagesFolder = databaseApplicationProperties.getSavedImagesFolderCreatingItIfNecessary();
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}

		setCurrentFilePathSelection(null);
		
		// ShutdownHook will run regardless of whether Command-Q (on Mac) or window closed ...
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				//System.err.println("DicomImageViewer.ShutdownHook.run()");
				if (databaseInformationModel != null) {		// may have failed to be initialized for some reason
					databaseInformationModel.close();	// we want to shut it down and compact it before exiting
				}
				if (networkApplicationInformation != null && networkApplicationInformation instanceof NetworkApplicationInformationFederated) {
					((NetworkApplicationInformationFederated)networkApplicationInformation).removeAllSources();
				}
				//System.err.print(TransferMonitor.report());
			}
		});

		System.err.println("Building GUI ...");

		cursorChanger = new SafeCursorChanger(this);
		
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		applicationWidth  = (int)(screenSize.getWidth())  - 20;
		applicationHeight = (int)(screenSize.getHeight()) - 70;
		imagesPerRow=1;
		imagesPerCol=1;
		
		Container content = getContentPane();
		EmptyBorder emptyBorder = (EmptyBorder)BorderFactory.createEmptyBorder();
		
		multiPanel = new JPanel();
		multiPanel.setLayout(new GridLayout(imagesPerCol,imagesPerRow));
		multiPanel.setBackground(Color.black);
		multiPanel.setOpaque(true);				// normally the default, but not for Quaqua - if not set, grey rather than black background will show through
		//multiPanel.setBorder(emptyBorder);

		referenceImagePanelForImages = new JPanel();
		referenceImagePanelForImages.setLayout(new GridLayout(1,1));
		referenceImagePanelForImages.setBackground(Color.black);
		//multiPanel.setBorder(emptyBorder);
		//referenceImagePanelForImages.setSize(new Dimension(128,128));
		referenceImagePanelForImages.setPreferredSize(new Dimension(128,128));
		//referenceImagePanelForImages.setMinimumSize(new Dimension(128,128));
		//referenceImagePanelForImages.setMaximumSize(new Dimension(128,128));

		referenceImagePanelForSpectra = new JPanel();
		referenceImagePanelForSpectra.setLayout(new GridLayout(1,1));
		referenceImagePanelForSpectra.setBackground(Color.black);
		//multiPanel.setBorder(emptyBorder);
		//referenceImagePanelForSpectra.setSize(new Dimension(128,128));
		referenceImagePanelForSpectra.setPreferredSize(new Dimension(128,128));
		//referenceImagePanelForSpectra.setMinimumSize(new Dimension(128,128));
		//referenceImagePanelForSpectra.setMaximumSize(new Dimension(128,128));

		scrollPaneOfCurrentAttributes = new JScrollPane();	// declared final because accessed from inner class (tab change action database refresh)
		//scrollPaneOfCurrentAttributes.setBorder(emptyBorder);
		createTableOfCurrentAttributesForCurrentFrameBrowser();
		scrollPaneOfCurrentAttributes.setViewportView(getTableOfCurrentAttributesForCurrentFrameBrowser());

		attributeFrameTableScrollPane=new JScrollPane();
		//attributeTreeScrollPane.setBorder(emptyBorder);
		createTableOfCurrentAttributesForAllFramesBrowser();
		attributeFrameTableScrollPane.setViewportView(getTableOfCurrentAttributesForAllFramesBrowser());

		//displayControlsPanel = new JPanel();
		//displayControlsPanel.setLayout(new GridLayout(3,1));
		//displayControlsPanel.setLayout(new BorderLayout());
		JPanel displayButtonsPanel = new JPanel();
		//displayControlsPanel.add(displayButtonsPanel);
		//displayControlsPanel.add(displayButtonsPanel,BorderLayout.NORTH);
		displayButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		//displayControlsPanel.setBorder(emptyBorder);
		
		{
			JPanel displayControlsSubPanel = new JPanel();
			//displayControlsPanel.add(displayControlsSubPanel,BorderLayout.CENTER);
			displayControlsSubPanel.setLayout(new BorderLayout());

			SourceImageSortOrderPanel displaySortPanel = new SourceImageSortOrderPanel(WellKnownContext.MAINPANEL);
			displayControlsSubPanel.add(displaySortPanel,BorderLayout.NORTH);

			{
				JPanel displayControlsSubSubPanel = new JPanel();
				displayControlsSubSubPanel.setLayout(new GridLayout(5,1));
				displayControlsSubPanel.add(displayControlsSubSubPanel,BorderLayout.SOUTH);

				sourceImageVOILUTSelectorPanel = new SourceImageVOILUTSelectorPanel(null/* Apply to all contexts, not just WellKnownContext.MAINPANEL*/);
				displayControlsSubSubPanel.add(sourceImageVOILUTSelectorPanel);
		
				sourceImageWindowLinearCalculationSelectorPanel = new SourceImageWindowLinearCalculationSelectorPanel(null/* Apply to all contexts, not just WellKnownContext.MAINPANEL*/);
				displayControlsSubSubPanel.add(sourceImageWindowLinearCalculationSelectorPanel);
		
				sourceImageWindowingAccelerationSelectorPanel = new SourceImageWindowingAccelerationSelectorPanel(null/* Apply to all contexts, not just WellKnownContext.MAINPANEL*/);
				displayControlsSubSubPanel.add(sourceImageWindowingAccelerationSelectorPanel);
		
				sourceImageGraphicDisplaySelectorPanel = new SourceImageGraphicDisplaySelectorPanel(null/* Apply to all contexts, not just WellKnownContext.MAINPANEL*/);
				displayControlsSubSubPanel.add(sourceImageGraphicDisplaySelectorPanel);
		
				sourceImageShutterSelectorPanel = new SourceImageShutterSelectorPanel(null/* Apply to all contexts, not just WellKnownContext.MAINPANEL*/);
				displayControlsSubSubPanel.add(sourceImageShutterSelectorPanel);
			}
		}
		
		{
			JPanel referenceSubPanel = new JPanel(new BorderLayout());
			//displayControlsPanel.add(referenceSubPanel,BorderLayout.SOUTH);
			
			JPanel referenceImageSubPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));		// nest these to make image centered and not fill width with black
			referenceSubPanel.add(referenceImageSubPanel,BorderLayout.CENTER);
			referenceImageSubPanel.add(referenceImagePanelForImages);

			displayListOfPossibleReferenceImagesForImages = new JList();
			displayListOfPossibleReferenceImagesForImages.setVisibleRowCount(4);	// need enough height for vertical scroll bar to show, including if horizontal scroll activates
			JScrollPane scrollingDisplayListOfPossibleReferenceImages = new JScrollPane(displayListOfPossibleReferenceImagesForImages);
			
			referenceSubPanel.add(scrollingDisplayListOfPossibleReferenceImages,BorderLayout.NORTH);
			
			displayListOfPossibleReferenceImagesForImages.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			displayListOfPossibleReferenceImagesForImages.addListSelectionListener(new OurReferenceListSelectionListener(referenceImagePanelForImages,false));
		}

		JPanel spectroscopyButtonsPanel = new JPanel();
		spectroscopyButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		JButton spectroscopyFileButton = new JButton("File...");
		spectroscopyFileButton.setToolTipText("Choose a DICOM image or spectroscopy file to display or DICOMDIR file to browse");
		spectroscopyButtonsPanel.add(spectroscopyFileButton);
		JButton spectroscopyImportButton = new JButton("Import");
		spectroscopyImportButton.setToolTipText("Import a copy of displayed specra into the local database");
		spectroscopyButtonsPanel.add(spectroscopyImportButton);
		JButton spectroscopySendButton = new JButton("Send...");
		spectroscopySendButton.setToolTipText("Send display spectra via DICOM network");
		spectroscopyButtonsPanel.add(spectroscopySendButton);
		JButton spectroscopyXMLButton = new JButton("XML...");
		spectroscopyXMLButton.setToolTipText("Save displayed spectra attributes to XML file");
		spectroscopyButtonsPanel.add(spectroscopyXMLButton);
		JButton spectroscopyValidateButton = new JButton("Validate...");
		spectroscopyValidateButton.setToolTipText("Validate displayed spectra against standard IOD");
		spectroscopyButtonsPanel.add(spectroscopyValidateButton);
		
		SourceSpectrumSortOrderPanel spectroscopySortPanel = new SourceSpectrumSortOrderPanel(WellKnownContext.MAINPANEL);
		
		{
			JPanel spectroscopyBackgroundAndReferenceGroupPanel = new JPanel(new BorderLayout());
			{
				JPanel backgroundSubPanel = new JPanel(new BorderLayout());
				spectroscopyBackgroundAndReferenceGroupPanel.add(backgroundSubPanel,BorderLayout.NORTH);
			
				displayListOfPossibleBackgroundImagesForSpectra = new JList();
				displayListOfPossibleBackgroundImagesForSpectra.setVisibleRowCount(4);	// need enough height for vertical scroll bar to show, including if horizontal scroll activates
				JScrollPane scrollingDisplayListOfPossibleBackgroundImages = new JScrollPane(displayListOfPossibleBackgroundImagesForSpectra);
			
				backgroundSubPanel.add(scrollingDisplayListOfPossibleBackgroundImages,BorderLayout.NORTH);
			
				displayListOfPossibleBackgroundImagesForSpectra.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				displayListOfPossibleBackgroundImagesForSpectra.addListSelectionListener(new OurBackgroundListSelectionListener());
			}
			{
				JPanel referenceSubPanel = new JPanel(new BorderLayout());
				spectroscopyBackgroundAndReferenceGroupPanel.add(referenceSubPanel,BorderLayout.SOUTH);
			
				JPanel referenceImageSubPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));		// nest these to make image centered and not fill width with black
				referenceSubPanel.add(referenceImageSubPanel,BorderLayout.CENTER);
				referenceImageSubPanel.add(referenceImagePanelForSpectra);

				displayListOfPossibleReferenceImagesForSpectra = new JList();
				displayListOfPossibleReferenceImagesForSpectra.setVisibleRowCount(4);	// need enough height for vertical scroll bar to show, including if horizontal scroll activates
				JScrollPane scrollingDisplayListOfPossibleReferenceImages = new JScrollPane(displayListOfPossibleReferenceImagesForSpectra);
			
				referenceSubPanel.add(scrollingDisplayListOfPossibleReferenceImages,BorderLayout.NORTH);
			
				displayListOfPossibleReferenceImagesForSpectra.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
				displayListOfPossibleReferenceImagesForSpectra.addListSelectionListener(new OurReferenceListSelectionListener(referenceImagePanelForSpectra,true));
			}
		}

		JPanel dicomdirButtonsPanel = new JPanel();
		dicomdirButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		JButton dicomdirViewSelectionButton = new JButton("View");
		dicomdirViewSelectionButton.setToolTipText("Display the image selected (or first image of the selection)");
		dicomdirButtonsPanel.add(dicomdirViewSelectionButton);
		JButton dicomdirSendButton = new JButton("Send...");
		dicomdirSendButton.setToolTipText("Send all the images selected via DICOM network");
		dicomdirButtonsPanel.add(dicomdirSendButton);
		dicomdirTreeScrollPane=new JScrollPane();
		
		JPanel queryButtonsPanel = new JPanel();
		queryButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));	// The use of FlowLayout means the buttons will disappear if browserPane gets too narrow
		JButton querySelectButton = new JButton("Select");
		querySelectButton.setToolTipText("Select the remote system to use for subsequent queries");
		queryButtonsPanel.add(querySelectButton);
		JButton queryFilterButton = new JButton("Filter");
		queryFilterButton.setToolTipText("Configure the filter to use for subsequent queries");
		queryButtonsPanel.add(queryFilterButton);
		JButton queryRefreshButton = new JButton("Query");
		queryRefreshButton.setToolTipText("Query the currently selected remote system to update the browser");
		queryButtonsPanel.add(queryRefreshButton);
		JButton queryRetrieveButton = new JButton("Retrieve");
		queryRetrieveButton.setToolTipText("Retrieve the selection to the local database");
		queryButtonsPanel.add(queryRetrieveButton);
		queryTreeScrollPane=new JScrollPane();

		final JPanel attributeTreeControlsPanel = new JPanel();
		attributeTreeControlsPanel.setLayout(new BorderLayout());
		final JPanel attributeTreeButtonsPanel = new JPanel();
		attributeTreeControlsPanel.add(attributeTreeButtonsPanel,BorderLayout.NORTH);
		attributeTreeButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		attributeTreeScrollPane=new JScrollPane();
		
		attributeTreeButtonsPanel.add(new JLabel("Sort attributes:"));

		attributeTreeSortOrderButtons = new ButtonGroup();
		SortAttributesActionListener sortAttributesActionListener = new SortAttributesActionListener();

		JRadioButton sortAttributesByNameButton = new JRadioButton("by name",true);
		sortAttributesByNameButton.setActionCommand(SortAttributesActionListener.ByName);
		sortAttributesByNameButton.setToolTipText("Sort attributes in tree alphabetically by name");
		sortAttributesByNameButton.addActionListener(sortAttributesActionListener);
		attributeTreeSortOrderButtons.add(sortAttributesByNameButton);
		attributeTreeButtonsPanel.add(sortAttributesByNameButton);

		JRadioButton sortAttributesByTagNumberButton = new JRadioButton("by number",false);
		sortAttributesByTagNumberButton.setActionCommand(SortAttributesActionListener.ByNumber);
		sortAttributesByTagNumberButton.setToolTipText("Sort attributes in tree numerically by group and element number");
		sortAttributesByTagNumberButton.addActionListener(sortAttributesActionListener);
		attributeTreeSortOrderButtons.add(sortAttributesByTagNumberButton);
		attributeTreeButtonsPanel.add(sortAttributesByTagNumberButton);

		//attributeTreeScrollPane.setBorder(emptyBorder);
		attributeTreeControlsPanel.add(attributeTreeScrollPane,BorderLayout.CENTER);

		//structuredReportTreeControlsPanel = new JPanel();
		//structuredReportTreeControlsPanel.setLayout(new BorderLayout());
		JPanel structuredReportTreeButtonsPanel = new JPanel();
		//structuredReportTreeControlsPanel.add(structuredReportTreeButtonsPanel,BorderLayout.NORTH);
		structuredReportTreeButtonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		JButton structuredReportTreeFileButton = new JButton("File...");
		structuredReportTreeFileButton.setToolTipText("Choose a DICOM SR or image or spectroscopy file to display or DICOMDIR file to browse");
		structuredReportTreeButtonsPanel.add(structuredReportTreeFileButton);
		JButton structuredReportTreeImportButton = new JButton("Import");
		structuredReportTreeImportButton.setToolTipText("Import a copy of displayed SR into the local database");
		structuredReportTreeButtonsPanel.add(structuredReportTreeImportButton);
		JButton structuredReportTreeSendButton = new JButton("Send...");
		structuredReportTreeSendButton.setToolTipText("Send displayed SR via DICOM network");
		structuredReportTreeButtonsPanel.add(structuredReportTreeSendButton);
		JButton structuredReportTreeXMLButton = new JButton("XML...");
		structuredReportTreeXMLButton.setToolTipText("Save displayed SR to XML file");
		structuredReportTreeButtonsPanel.add(structuredReportTreeXMLButton);
		JButton structuredReportTreeValidateButton = new JButton("Validate...");
		structuredReportTreeValidateButton.setToolTipText("Validate displayed SR against standard IOD and templates");
		structuredReportTreeButtonsPanel.add(structuredReportTreeValidateButton);
		
		structuredReportTreeScrollPane=new JScrollPane();
		initializeCurrentRemoteQueryFilter();
		
		JLabel statusBar = getStatusBar();

//System.err.println("Loading DICOM file or chooser ...");
		if (dicomFileName == null) {
			lastDirectoryPath = null;
		}
		else {
			lastDirectoryPath = new File(dicomFileName).getParent();
			loadDicomFileOrDirectory(dicomFileName,multiPanel,
				referenceImagePanelForImages,
				referenceImagePanelForSpectra);
		}
		
		//System.err.println("Building action listeners ...");
		DicomFileOrDirectoryLoadActionListener dicomFileOrDirectoryLoadActionListener = 
			new DicomFileOrDirectoryLoadActionListener(multiPanel,
				referenceImagePanelForImages,
				referenceImagePanelForSpectra);

		spectroscopyFileButton.addActionListener(dicomFileOrDirectoryLoadActionListener);
		structuredReportTreeFileButton.addActionListener(dicomFileOrDirectoryLoadActionListener);
		
		ImportCurrentlyDisplayedInstanceToDatabaseActionListener importCurrentlyDisplayedInstanceToDatabaseActionListener = new ImportCurrentlyDisplayedInstanceToDatabaseActionListener();
			
		spectroscopyImportButton.addActionListener(importCurrentlyDisplayedInstanceToDatabaseActionListener);
		structuredReportTreeImportButton.addActionListener(importCurrentlyDisplayedInstanceToDatabaseActionListener);

		queryRefreshButton.addActionListener(new QueryRefreshActionListener());
		queryRetrieveButton.addActionListener(new QueryRetrieveActionListener());

		NetworkSendCurrentSelectionActionListener dicomFileOrDirectoryOrDatabaseSendActionListener = 
			new NetworkSendCurrentSelectionActionListener();
		dicomdirSendButton.addActionListener(dicomFileOrDirectoryOrDatabaseSendActionListener);
		
		spectroscopySendButton.addActionListener(dicomFileOrDirectoryOrDatabaseSendActionListener);
		structuredReportTreeSendButton.addActionListener(dicomFileOrDirectoryOrDatabaseSendActionListener);
		
		SaveCurrentlyDisplayedImageToXMLActionListener saveCurrentlyDisplayedImageToXMLActionListener = 
			new SaveCurrentlyDisplayedImageToXMLActionListener();
		spectroscopyXMLButton.addActionListener(saveCurrentlyDisplayedImageToXMLActionListener);
		
		SaveCurrentlyDisplayedStructuredReportToXMLActionListener saveCurrentlyDisplayedStructuredReportToXMLActionListener = 
			new SaveCurrentlyDisplayedStructuredReportToXMLActionListener();
		structuredReportTreeXMLButton.addActionListener(saveCurrentlyDisplayedStructuredReportToXMLActionListener);
		
//System.err.println("Building ValidateCurrentlyDisplayedImageActionListener ...");
		ValidateCurrentlyDisplayedImageActionListener validateCurrentlyDisplayedImageActionListener = 
			new ValidateCurrentlyDisplayedImageActionListener();
		spectroscopyValidateButton.addActionListener(validateCurrentlyDisplayedImageActionListener);
		
		ValidateCurrentlyDisplayedStructuredReportActionListener validateCurrentlyDisplayedStructuredReportActionListener = 
			new ValidateCurrentlyDisplayedStructuredReportActionListener();
		structuredReportTreeValidateButton.addActionListener(validateCurrentlyDisplayedStructuredReportActionListener);
		
		// Layout the rest of the GUI components ...
		
		

		Box mainPanel = new Box(BoxLayout.Y_AXIS);
		mainPanel.add(multiPanel);
		// make label really wide, else doesn't completely repaint on status update
		mainPanel.add(statusBar);
		content.add(mainPanel);

		Dimension multiPanelDimension = defaultMultiPanelDimension;
		multiPanel.setSize(multiPanelDimension);
		multiPanel.setPreferredSize(multiPanelDimension);
		
		{
			Dimension d = getTableOfCurrentAttributesForCurrentFrameBrowser().getPreferredSize();
			int w = (int)d.getWidth();
			int h = (int)d.getHeight();
			int wWanted = widthWantedForBrowser + (int)multiPanel.getPreferredSize().getWidth();
			if (w > wWanted) w = wWanted;
			if (h < heightWantedForAttributeTable) h=heightWantedForAttributeTable;
			scrollPaneOfCurrentAttributes.setPreferredSize(new Dimension(w,h));
		}
		
		// See "http://java.sun.com/docs/books/tutorial/extra/fullscreen/example-1dot4/DisplayModeTest.java"
		
		boolean allowFullScreen = false;
		{
			String fullScreen=properties.getProperty(propertyName_FullScreen);
			if (fullScreen != null && fullScreen.equals("true")) {
				allowFullScreen=true;
			}
		}
		GraphicsDevice[] devices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
		boolean isFullScreen = allowFullScreen && devices.length == 1 && devices[0].isFullScreenSupported();
		setUndecorated(isFullScreen);
		setResizable(!isFullScreen);
		if (isFullScreen) {
//System.err.println("Full screen ...");
			devices[0].setFullScreenWindow(this);
			validate();
		}
		else {
			// Windowed mode
			pack();
			setVisible(true);
		}
	}
	}
	
	// override ApplicationFrame methods and relevant constructors ...

	/**
	 * @param	title
	 * @param	w
	 * @param	h
	 */
	private DicomImageViewer(String title,int w,int h) { 
	} 

	/**
	 * @param	title
	 */
	private DicomImageViewer(String title) {
	} 

	/**
	 * @param	title
	 * @param	dicomFileName
	 */
	public DicomImageViewer(String title,String dicomFileName) {
		super(title, null);
		doCommonConstructorStuff(title, dicomFileName);
	}

	/**
	 * @param	title
	 * @param	applicationPropertyFileName
	 * @param	dicomFileName
	 */
	private DicomImageViewer(String title,String applicationPropertyFileName,String dicomFileName) {
		super(title,applicationPropertyFileName);
		doCommonConstructorStuff(title,dicomFileName);
	}
	
	/**
	 * <p>The method to invoke the application.</p>
	 *
	 * @param	arg	optionally, a single file which may be a DICOM object or DICOMDIR
	 */
	public static void main(String arg[]) {
		String dicomFileName = null;
		if (arg.length == 1) {
			dicomFileName=arg[0].trim();
			if (dicomFileName.length() == 0) {
				dicomFileName = null;
			}
		}
		
		if (System.getProperty("mrj.version") != null) {
			System.setProperty("apple.awt.fakefullscreen", "true");		// Must be done before creating components
		}
		
		//DicomImageViewer af = new DicomImageViewer("Dicom Image Viewer",propertiesFileName,dicomFileName);
		DicomImageViewer af = new DicomImageViewer("Dicom Image Viewer", dicomFileName);
	}
}








