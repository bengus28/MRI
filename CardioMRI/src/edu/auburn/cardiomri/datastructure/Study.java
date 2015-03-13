package edu.auburn.cardiomri.datastructure;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import edu.auburn.cardiomri.datastructure.Group.GroupComparator;

/**
 * This class represents a research study and all associated
 * data extracted from DICOM files at the time of creation.
 * Study serves as the root of the tree structure used 
 * and contains both images and analysis information.
 * 
 * @author Eric Turner
 *
 */
public class Study implements Serializable {

	public static final double GROUP_COMPARISON_EPSILON = 0.00001;
	
	private Map<String, DICOMImage>  SOPInstanceUIDtoDICOMImage = new HashMap<String, DICOMImage>();
	private String version;
	private String userID;
	private String studyID;
	// TODO: check numerical types Java vs MATLAB
	private int EDTimeFrame;
	private int ESTimeFrame;
	private double SystolicBloodPressure; // mmHg
	private double DiastolicBloodPressure; // mmHg

	// Eliminated 'nGroups' will be length of groups

	private ArrayList<Group> groups = new ArrayList<Group>();

	/** Returns the version of the study */
	public String getVersion() {
		return version;
	}
	
	/** 
	 * Sets the version of the study
	 * 
	 * @param version the new version identifier 
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	/** Returns the userID */
	public String getUserID() {
		return userID;
	}

	/**
	 * Sets the userID
	 * 
	 *  @param userID the new userID
	 */
	public void setUserID(String userID) {
		this.userID = userID;
	}

	/** Returns the studyID */
	public String getStudyID() {
		return studyID;
	}

	/**
	 * Sets the studyID
	 * 
	 * @param studyID the new studyID
	 */
	public void setStudyID(String studyID) {
		this.studyID = studyID;
	}

	/** Returns EDTimeFrame */
	public int getEDTimeFrame() {
		return EDTimeFrame;
	}

	/**
	 * Sets the EDTimeFrame
	 * 
	 * @param eDTimeFrame new EDTimeFrame
	 */
	public void setEDTimeFrame(int eDTimeFrame) {
		EDTimeFrame = eDTimeFrame;
	}

	/** Returns ESTimeFrame */
	public int getESTimeFrame() {
		return ESTimeFrame;
	}

	/**
	 * Sets the ESTimeFrame
	 * 
	 * @param eSTimeFrame new ESTimeFrame
	 */
	public void setESTimeFrame(int eSTimeFrame) {
		ESTimeFrame = eSTimeFrame;
	}

	/** Returns SystolicBloodPressure */
	public double getSystolicBloodPressure() {
		return SystolicBloodPressure;
	}

	/**
	 * Sets SystolicBloodPressure
	 * 
	 * @param systolicBloodPressure new SystolicBloodPressure
	 */
	public void setSystolicBloodPressure(double systolicBloodPressure) {
		SystolicBloodPressure = systolicBloodPressure;
	}

	/** Returns DiastolicBloodPressure */
	public double getDiastolicBloodPressure() {
		return DiastolicBloodPressure;
	}

	/**
	 * Sets the DiastolicBloodPressure
	 * 
	 * @param diastolicBloodPressure new DiastolicBloodPressure
	 */
	public void setDiastolicBloodPressure(double diastolicBloodPressure) {
		DiastolicBloodPressure = diastolicBloodPressure;
	}

	/** Returns the groups in the study */
	public ArrayList<Group> getGroups() {
		return groups;
	}

	/**
	 * Sets the groups for the study
	 * 
	 * @param groups new list of groups
	 */
	public void setGroups(ArrayList<Group> groups) {
		this.groups = groups;
	}
	
	/**
	 * Adds a DICOMImage to the Study. Determines which group the 
	 * DICOMImage belongs to.
	 * 
	 * @param image The DICOMImage to add
	 * @throws NotInStudyException 
	 * @author Tony Bernhardt
	 */
	public void addImage(DICOMImage image) throws NotInStudyException {
		if (this.getStudyID() == null) {
			this.setStudyID(image.getStudyID());
		}
		
		if (!image.getStudyID().equals(this.getStudyID())) {
			throw new NotInStudyException(image.getSopInstanceUID());
		}
		Vector3d imgRowsVector = new Vector3d(
				image.getImageOrientationPatient()[0],
				image.getImageOrientationPatient()[1],
				image.getImageOrientationPatient()[2]);
		Vector3d imgColVector = new Vector3d(
				image.getImageOrientationPatient()[3],
				image.getImageOrientationPatient()[4],
				image.getImageOrientationPatient()[5]);
		Vector3d imgStackUnitVector = imgRowsVector.cross(imgColVector).unit();
		
		for (Group group : groups) {
			if (image.getSeriesNumber() == group.getSeriesNumber()) {
				
				
				//Vector3d groupStackUnitVector = group.getStackUnitVector();
				//
				//if (Math.abs(imgStackUnitVector.getX() - groupStackUnitVector.getX()) > GROUP_COMPARISON_EPSILON) continue;
				//if (Math.abs(imgStackUnitVector.getY() - groupStackUnitVector.getY()) > GROUP_COMPARISON_EPSILON) continue;
				//if (Math.abs(imgStackUnitVector.getZ() - groupStackUnitVector.getZ()) > GROUP_COMPARISON_EPSILON) continue;
				group.addImage(image);
				SOPInstanceUIDtoDICOMImage.put(image.getSopInstanceUID(), image);
				return;
			}
		}
	
		
		// Matching Group not found
		Group group = new Group();
		group.setSeriesNumber(image.getSeriesNumber());
		group.setSeriesDescription(image.getSeriesDescription());
		group.setStackUnitVector(imgStackUnitVector);
		group.addImage(image);
		groups.add(group);
		Collections.sort(groups, new GroupComparator());
		return;
	}
	
	
	/**
	 * gets the image associated with a given SOPInstanceUID
	 * 
	 * @param SOPInstanceUID
	 * @return
	 */
	public DICOMImage getImage(String SOPInstanceUID){
		return this.SOPInstanceUIDtoDICOMImage.get(SOPInstanceUID);
	}
	
	
	/**
	 * The Exception class representing the occurance of a DICOMImage being
	 * attempted to be added to a Study it does not belong to.
	 * 
	 * @author Tony Bernhardt
	 *
	 */
	public class NotInStudyException extends Exception {
		public NotInStudyException(String sopInstanceUID) {
			super(sopInstanceUID);
		}
	}


	public Map<String, DICOMImage> getSOPInstanceUIDToDICOMImage() {
		return this.SOPInstanceUIDtoDICOMImage;
		//e
	}
}
