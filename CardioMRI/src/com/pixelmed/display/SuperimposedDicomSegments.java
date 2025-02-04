/* Copyright (c) 2001-2013, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.display;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.GeometryOfVolumeFromAttributeList;
import com.pixelmed.dicom.SequenceAttribute;
import com.pixelmed.dicom.SOPClass;
import com.pixelmed.dicom.TagFromName;

import com.pixelmed.geometry.GeometryOfVolume;

import java.io.IOException;

import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * <p>A class that supports extracting DICOM segmentation objects with one or more segments
 * as superimposed images.</p>
 *
 * @author	dclunie
 */

public class SuperimposedDicomSegments {

	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/display/SuperimposedDicomSegments.java,v 1.2 2013/10/20 16:46:18 dclunie Exp $";
	
	/**
	 * @param	list
	 */
	public SuperimposedDicomSegments(AttributeList list) throws DicomException {
		// no need to call super(), does nothing
		doCommonConstructorStuff(list);
	}
	
	/**
	 * @param	filename
	 */
	public SuperimposedDicomSegments(String filename) throws DicomException, IOException {
		// no need to call super(), does nothing
		AttributeList list = new AttributeList();
		list.read(filename);
		doCommonConstructorStuff(list);
	}
	
	protected class SegmentInformation {
		String segmentNumber;
		int[] recommendedDisplayCIELabValues;
		
		public SegmentInformation(String segmentNumber,int[] recommendedDisplayCIELabValues) {
			this.segmentNumber = segmentNumber;
			this.recommendedDisplayCIELabValues = recommendedDisplayCIELabValues;
		}
	}
	
	protected SortedMap<String,SegmentInformation> segmentInformationBySegmentNumber = new TreeMap<String,SegmentInformation>();
	protected SortedMap<String,SuperimposedImage> superimposedImagesBySegmentNumber = new TreeMap<String,SuperimposedImage>();
	protected SortedMap<String,SortedSet<Integer>> framesForSegmentBySegmentNumber = new TreeMap<String,SortedSet<Integer>>();
	
	/**
	 * @param	list
	 */
	private void doCommonConstructorStuff(AttributeList list) throws DicomException {
		String sopClassUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.SOPClassUID);
		if (SOPClass.isImageStorage(sopClassUID)) {
//System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): is an image");
			SourceImage allFramesImage = new SourceImage(list);
			Attribute aSegmentSequence = list.get(TagFromName.SegmentSequence);
			Attribute aSharedFunctionalGroupsSequence = list.get(TagFromName.SharedFunctionalGroupsSequence);
			Attribute aPerFrameFunctionalGroupsSequence = list.get(TagFromName.PerFrameFunctionalGroupsSequence);
			int numberOfFrames = allFramesImage.getNumberOfBufferedImages();
			if (allFramesImage != null && numberOfFrames > 0
			 && aSegmentSequence != null && aSegmentSequence instanceof SequenceAttribute
			 && aSharedFunctionalGroupsSequence != null && aSharedFunctionalGroupsSequence instanceof SequenceAttribute
			 && aPerFrameFunctionalGroupsSequence != null && aPerFrameFunctionalGroupsSequence instanceof SequenceAttribute) {
System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): have a segmentation object with one or more frames");
				SequenceAttribute saSegmentSequence = (SequenceAttribute)aSegmentSequence;
				int numberOfSegments = saSegmentSequence.getNumberOfItems();
				if (numberOfSegments > 0) {
					for (int i=0; i<numberOfSegments; ++i) {
						AttributeList itemList = saSegmentSequence.getItem(i).getAttributeList();
						String segmentNumber = Attribute.getSingleStringValueOrEmptyString(itemList,TagFromName.SegmentNumber);	// theoretically supposed to start at 1 and increase by 1, but don't trust it
						if (segmentNumber.length() > 0) {
							int[] recommendedDisplayCIELabValues = Attribute.getIntegerValues(itemList,TagFromName.RecommendedDisplayCIELabValue);
							SegmentInformation si = segmentInformationBySegmentNumber.get(segmentNumber);
							if (si == null) {
								si = new SegmentInformation(segmentNumber,recommendedDisplayCIELabValues);
								segmentInformationBySegmentNumber.put(segmentNumber,si);
							}
							else {
								throw new DicomException("Duplicate segment number");
							}
						}
						else {
							throw new DicomException("Missing segment number");
						}
					}
				}
				else {
System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): Error: No segments in segmentation object");
				}
				
				SequenceAttribute saSharedFunctionalGroupsSequence = (SequenceAttribute)aSharedFunctionalGroupsSequence;
				SequenceAttribute saPerFrameFunctionalGroupsSequence = (SequenceAttribute)aPerFrameFunctionalGroupsSequence;
				SequenceAttribute sharedSegmentIdentificationSequence = (SequenceAttribute)(SequenceAttribute.getNamedAttributeFromWithinSequenceWithSingleItem(saSharedFunctionalGroupsSequence,TagFromName.SegmentIdentificationSequence));
				int nPerFrameFunctionalGroupsSequence = saPerFrameFunctionalGroupsSequence.getNumberOfItems();
				if (nPerFrameFunctionalGroupsSequence == numberOfFrames) {
					for (int f=0; f<numberOfFrames; ++f) {
						SequenceAttribute useSegmentIdentificationSequence = sharedSegmentIdentificationSequence;
						if (useSegmentIdentificationSequence == null) {
							useSegmentIdentificationSequence = (SequenceAttribute)(saPerFrameFunctionalGroupsSequence.getItem(f).getAttributeList().get(TagFromName.SegmentIdentificationSequence));
						}
						if (useSegmentIdentificationSequence != null) {
							Attribute aReferencedSegmentNumber = SequenceAttribute.getNamedAttributeFromWithinSequenceWithSingleItem(useSegmentIdentificationSequence,TagFromName.ReferencedSegmentNumber);
							String referencedSegmentNumber = aReferencedSegmentNumber == null ? "" : aReferencedSegmentNumber.getSingleStringValueOrEmptyString();
							if (referencedSegmentNumber.length() > 0) {
								SortedSet<Integer> framesForSegment = framesForSegmentBySegmentNumber.get(referencedSegmentNumber);
								if (framesForSegment == null) {
									framesForSegment = new TreeSet<Integer>();
									framesForSegmentBySegmentNumber.put(referencedSegmentNumber,framesForSegment);
								}
								framesForSegment.add(new Integer(f));
							}
							else {
								throw new DicomException("Missing ReferencedSegmentNumber for frame "+f);
							}
						}
						else {
							throw new DicomException("Missing SegmentIdentificationSequence for frame "+f);
						}
					}
				}
				else {
					throw new DicomException("Number of frames "+numberOfFrames+" does not match number of PerFrameFunctionalGroupsSequence items "+nPerFrameFunctionalGroupsSequence);
				}
				
				//SortedMap<String,SortedSet<Integer>> framesForSegmentBySegmentNumber
				for (String segmentNumber : framesForSegmentBySegmentNumber.keySet()) {
//System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): Making SourceImageSubset for segmentNumber "+segmentNumber);
					SortedSet<Integer> framesForSegment = framesForSegmentBySegmentNumber.get(segmentNumber);
					int[] parentFrameNumbers = new int[framesForSegment.size()];
					{
						int childFrameNumber = 0;
						for (Integer parentFrameNumber : framesForSegment) {
//System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): parentFrameNumber["+childFrameNumber+"] = "+parentFrameNumber);
							parentFrameNumbers[childFrameNumber++] = parentFrameNumber.intValue();
						}
					}
					SourceImage sourceImageForSegment = new SourceImageSubset(allFramesImage,parentFrameNumbers);
					GeometryOfVolume geometryForSegment = new GeometryOfVolumeFromAttributeList(list,parentFrameNumbers);
					if (!geometryForSegment.isVolumeSampledRegularlyAlongFrameDimension()) {
System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): Warning: superimposed geometry is not a single regularly sampled volume for segment "+segmentNumber);
					}
					superimposedImagesBySegmentNumber.put(segmentNumber,new SuperimposedImage(sourceImageForSegment,geometryForSegment,segmentInformationBySegmentNumber.get(segmentNumber).recommendedDisplayCIELabValues));
				}
				
				//superimposedGeometry = new GeometryOfVolumeFromAttributeList(list);
				//if (!superimposedGeometry.isVolumeSampledRegularlyAlongFrameDimension()) {
//System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): Warning: superimposed geometry is not a single regularly sampled volume");
				//}
			}
			else {
System.err.println("SuperimposedDicomSegments.doCommonConstructorStuff(): Error: Not a valid segmentation object");
			}
		}
	}

	/**
	 * @return	the superimposed images, one per segment
	 */
	public Vector<SuperimposedImage> getSuperimposedImages() throws DicomException {
		Vector<SuperimposedImage> superimposedImages = new Vector<SuperimposedImage>();
		for (String segmentNumber : superimposedImagesBySegmentNumber.keySet()) {
//System.err.println("SuperimposedDicomSegments.getSuperimposedImages(): Adding segmentNumber "+segmentNumber);
			superimposedImages.add(superimposedImagesBySegmentNumber.get(segmentNumber));
		}
		return superimposedImages;
	}
	
	/**
	 * @param	arg	the underlying image file name, the superimposed segmentation object file name, and optionally the file name basis for a consumer format image rendering
	 */
	public static void main(String arg[]) {
		try {
			String underlyingFileName = arg[0];
			String superimposedFileName = arg[1];
			
			SuperimposedDicomSegments superimposedSegments = new SuperimposedDicomSegments(superimposedFileName);
			Vector<SuperimposedImage> superimposedImages =  superimposedSegments.getSuperimposedImages();
			
			if (arg.length > 2) {
				String outputFileName = arg[2];
				ConsumerFormatImageMaker.convertFileToEightBitImage(underlyingFileName,outputFileName,"jpeg",
					0/*windowCenter*/,0/*windowWidth*/,0/*imageWidth*/,0/*imageHeight*/,100/*imageQuality*/,ConsumerFormatImageMaker.ALL_ANNOTATIONS,
					superimposedImages,null/*arrayOfPerFrameShapes*/,0/*debugLevel*/);
			}
			else {
				AttributeList underlyingList = new AttributeList();
				underlyingList.read(underlyingFileName);
				SourceImage underlyingSourceImage = new SourceImage(underlyingList);
				GeometryOfVolume underlyingGeometry = new GeometryOfVolumeFromAttributeList(underlyingList);

				SingleImagePanel ip = new SingleImagePanel(underlyingSourceImage,null,underlyingGeometry);
				ip.setSuperimposedImages(superimposedImages);
				javax.swing.JFrame frame = new javax.swing.JFrame();
				frame.add(ip);
				frame.setSize(underlyingSourceImage.getWidth(),underlyingSourceImage.getHeight());
				frame.setVisible(true);
			}
		}
		catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
}

