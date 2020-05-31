package spermQMFGenLUT_jnh;

import java.awt.Font;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import javax.swing.UIManager;

import ij.*;
import ij.gui.GenericDialog;
import ij.measure.*;
import ij.plugin.*;
import ij.text.TextPanel;
import spermQMFGenLUT_jnh.tools.OpenFilesDialog;
import spermQMFGenLUT_jnh.tools.ProgressDialog;

/***===============================================================================

SpermQ-MF Generate LUT Version v0.1.0 (20160816)

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation (http://www.gnu.org/licenses/gpl.txt )

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

See the GNU General Public License for more details.

Copyright (C) 2016 - 2020: @author Jan N Hansen
  
For any questions please feel free to contact me (jan.hansen(at)uni-bonn.de).

==============================================================================**/
public class spqmfGenLUTMain implements PlugIn, Measurements{
	static final String PLUGINNAME = "SpermQ-MF Generate LUT";
	static final String PLUGINVERSION = "0.1.0";
	
	//Settings
		int LUTHeight = 301;
		int LUTRadius = ((int)Math.round((LUTHeight-1)/2));
		int medianBlurRadiusAl = 3;
		int medianBlurRadiusPos = 3;
		int minimumFindingRadius = 10;
		
	//Fonts
			static final Font Heading1 = new Font("Sansserif", Font.BOLD, 16);
			static final Font Heading2 = new Font("Sansserif", Font.BOLD, 14);
			static final Font BoldText = new Font("Sansserif", Font.BOLD, 12);
			static final Font PlainText = new Font("Sansserif", Font.PLAIN, 12);
				
	//Formats
		static final SimpleDateFormat shortDate = new SimpleDateFormat("yyMMdd_HHmmss");
		static final SimpleDateFormat fullDate = new SimpleDateFormat("yyyy-MM-dd	HH:mm:ss");
		static final SimpleDateFormat yearOnly = new SimpleDateFormat("yyyy");
		static final DecimalFormat dformat6 = new DecimalFormat("#0.000000");
		static final DecimalFormat dformat3 = new DecimalFormat("#0.000");
		static final DecimalFormat dformat0 = new DecimalFormat("#0");
		static final DecimalFormat dformatdialog = new DecimalFormat("#0.000000");
				
	ProgressDialog progress;	
	boolean done = false;
	
	//Varibales
	int maxArcL = 0;
	int maxZStep = 0;
	double maxResolution = 0.0;
	double minWidthAll = Double.POSITIVE_INFINITY;
	double maxWidthAll = 0.0;
	
	@Override
	public void run(String arg) {
		//settings
		GenericDialog gd = new GenericDialog(PLUGINNAME + " v" + PLUGINVERSION);		
//		setInsets(top, left, bottom)
		gd.setInsets(0,0,0);	gd.addMessage(PLUGINNAME + ", version " + PLUGINVERSION + " (\u00a9 2016-" 
				+ yearOnly.format(new Date()) + ", JN Hansen)", Heading1);
		
		gd.setInsets(10,0,0);	gd.addNumericField("LUT height", LUTHeight, 0);
		gd.setInsets(10,0,0);	gd.addNumericField("(+/-)-position distance to find minimum", minimumFindingRadius, 0);
		gd.setInsets(10,0,0);	gd.addNumericField("Median blur radius (arc-length)", medianBlurRadiusAl, 0);
		gd.setInsets(10,0,0);	gd.addNumericField("Median blur radius (z position)", medianBlurRadiusPos, 0);
		
		gd.showDialog();
		
		LUTHeight = (int) gd.getNextNumber();
		if(LUTHeight%2 == 0) LUTHeight++;
		LUTRadius = ((int)Math.round((LUTHeight-1)/2));
		minimumFindingRadius = (int)gd.getNextNumber();
		medianBlurRadiusAl = (int)gd.getNextNumber();
		medianBlurRadiusPos = (int)gd.getNextNumber();
		
		if (gd.wasCanceled())	return;
		
		//variables
		int tasks = 1;
		String [] name, dir;
		
		//open files
		{
			OpenFilesDialog od = new OpenFilesDialog ();
			od.setLocation(0,0);
			od.setVisible(true);
			
			od.addWindowListener(new java.awt.event.WindowAdapter() {
		        public void windowClosing(WindowEvent winEvt) {
		        	//Analysis canceled!
		        	return;
		        }
		    });
					
			//Waiting for od to be done
			while(od.done==false){
				 try{
					 Thread.currentThread().sleep(50);
			     }catch(Exception e){
			     }
			}
			
			tasks = od.filesToOpen.size();
			name = new String [tasks];
			dir = new String [tasks];
			for(int task = 0; task < tasks; task++){
				name[task] = od.filesToOpen.get(task).getName();
				dir[task] = od.filesToOpen.get(task).getParent() + System.getProperty("file.separator");
			}			
		}	
		try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception e){}
		
		progress = new ProgressDialog(new String[]{PLUGINNAME + "",""},1);
		progress.setLocation(0,0);
		progress.setVisible(true);
		progress.addWindowListener(new java.awt.event.WindowAdapter() {
	        public void windowClosing(WindowEvent winEvt) {
	        	progress.stopProcessing();
	        	if(done==false){
	        		IJ.error("Script stopped...");
	        	}       	
	        	return;
	        }
		});
		progress.replaceBarText("started...");
		
		double [] minWidth = new double [tasks];
		double [] maxWidth = new double [tasks];
		double [] resolution = new double [tasks];
		
		ImagePlus imp;
		
		for(int task = 0; task < tasks; task++){
			progress.replaceBarText("pre-analyzing LUT " + (task+1) + "/" + tasks);
			imp = IJ.openImage(dir[task]+name[task]);
			
			//read information about width max and min
			try {
				FileReader fr = new FileReader(new File(dir[task] + name[task].substring(0, name[task].lastIndexOf(".")) + "_info.txt"));
				BufferedReader br = new BufferedReader(fr);
				String line = "";
				for(int i = 0; i < 9; i++){
					line = br.readLine();
				}
							
				if(line.contains("#")){
					minWidth [task] = Double.parseDouble(line.substring(line.indexOf("#")+1, line.indexOf("-")));
//					if(minWidth [task] < minWidthAll)	minWidthAll = minWidth [task];
					maxWidth [task] = Double.parseDouble(line.substring(line.indexOf("-")+1, line.indexOf("+")));
//					if(maxWidth [task] > maxWidthAll)	maxWidthAll = maxWidth [task];
					resolution [task] = Double.parseDouble(line.substring(line.indexOf("+")+1));
//					IJ.log("res " + resolution [task]);
					if(resolution[task] > maxResolution)	maxResolution = resolution [task];
				}
				
				br.close();
				fr.close();
			}catch (IOException e) {
				progress.notifyMessage("problem with text loading", ProgressDialog.ERROR);
				e.printStackTrace();
			}			
			
			if((int)Math.round(imp.getWidth()/resolution[task]) > (maxArcL)){
				maxArcL = (int)Math.round(imp.getWidth()/resolution[task]);
			}
//			IJ.log("mal " + maxArcL);
			if(imp.getHeight() > maxZStep){
				maxZStep = imp.getHeight();
			}
//			IJ.log("mzs " + maxZStep);		
			imp.close();			
		}
		
		double [][][] output = new double [(int)Math.round(maxArcL*maxResolution) + 1][LUTHeight][4];
		double [][][] output2 = new double [(int)Math.round(maxArcL*maxResolution) + 1][LUTHeight][4];
		int [][][] outputCt = new int [(int)Math.round(maxArcL*maxResolution) + 1][LUTHeight][4];
		ImagePlus outputImp = IJ.createImage("Merged LUT", (int)Math.round(maxArcL*maxResolution) + 1, LUTHeight, 4, 8);
		
		//initialized			
		for(int s = 0; s < 4; s++){		
			for(int al = 0; al < (int)Math.round(maxArcL*maxResolution)+1; al++){
				for(int pos = 0; pos < LUTHeight; pos++){
					output [al][pos][s] = 0.0;
					output2 [al][pos][s] = 0.0;
					outputCt [al][pos][s] = 0;
				}
			}
		}
			
		int cPos, counter; double value, minValue, minSingleValue;
		for(int task = 0; task < tasks; task++){
			progress.updateBarText("analysis started...");
			imp = IJ.openImage(dir[task]+name[task]);
								
			for(int s = 0; s < 4; s++){		
				for(int al = 0; al < imp.getWidth(); al++){
					minSingleValue = Double.POSITIVE_INFINITY;
					for(int pos = 0; pos < imp.getHeight(); pos++){
						if(imp.getStack().getVoxel(al, pos, s) != 0.0 && imp.getStack().getVoxel(al, pos, s) < minSingleValue){
							minSingleValue = imp.getStack().getVoxel(al, pos, s);
						}
					}
					cPos = -1;
					minValue = Double.POSITIVE_INFINITY;
					for(int pos = 0; pos < imp.getHeight(); pos++){
						counter = 0;
						value = 0.0;
						
						if(imp.getStack().getVoxel(al, pos, s) > 0.0){
							value += imp.getStack().getVoxel(al, pos, s);
							counter ++;
						}
						
						for(int i = 1; i < minimumFindingRadius && pos+i < imp.getHeight(); i++){
							if(imp.getStack().getVoxel(al, pos+i, s) > 0.0){
								value += imp.getStack().getVoxel(al, pos+i, s);
								counter++;
							}
						}
						
						for(int i = 1; i < minimumFindingRadius && pos-i > 0; i++){
							if(imp.getStack().getVoxel(al, pos-i, s) > 0.0){
								value += imp.getStack().getVoxel(al, pos-i, s);
								counter++;
							}
						}
						
						if(counter > (minimumFindingRadius*2-5)){
							value /= counter;
							if(value < minValue){
								minValue = value;
								cPos = pos;
							}							
						}
					}
					if(cPos != -1 && mathAbs(minValue-minSingleValue) < 5.0){
						for(int pos = 0; pos < imp.getHeight(); pos++){
							if(imp.getStack().getVoxel(al, pos, s) > 0.0 
									&& LUTRadius + (pos-cPos) >= 0 
									&& LUTRadius + (pos-cPos) < LUTHeight ){
								output [(int)Math.round((al/resolution[task]) * maxResolution)][LUTRadius + (pos - cPos)][s] 
										+= getWidthFromIntensity(minWidth [task], maxWidth [task], imp.getStack().getVoxel(al, pos, s));
								outputCt [(int)Math.round((al/resolution[task]) * maxResolution)][LUTRadius + (pos - cPos)][s]++;
							}							
						}
					}else{
						IJ.log("minValue=" + minValue + " cPos=" + cPos);
					}					
				}
			}
			progress.updateBarText("done");
			progress.setBar(0.9*(task+1)/tasks);
			imp.close();			
		}
		
		for(int s = 0; s < 4; s++){		
			for(int al = 0; al < (int)Math.round(maxArcL*maxResolution)+1; al++){
				for(int pos = 0; pos < LUTHeight; pos++){
					if(outputCt [al][pos][s] > 0){
						output [al][pos][s] /= (double)outputCt [al][pos][s];
					}										
				}
			}
		}
		
		//median blur
		double [] medianArray = new double [(medianBlurRadiusAl*2+1)*(medianBlurRadiusPos*2+1)];
		for(int s = 0; s < 4; s++){		
			for(int al = 0; al < (int)Math.round(maxArcL*maxResolution)+1; al++){
				for(int pos = 0; pos < LUTHeight; pos++){
					Arrays.fill(medianArray, Double.POSITIVE_INFINITY);
					counter = 0;
										
					for(int i = 0; i <= medianBlurRadiusAl && (al-i) > 0; i++){
						if(outputCt [al-i][pos][s] > 0){
							medianArray [counter] = output [al-i][pos][s];
							counter++;
						}	
						for(int j = 1; j <= medianBlurRadiusPos && (pos-j) > 0; j++){
							if(outputCt [al-i][pos-j][s] > 0){
								medianArray [counter] = output [al-i][pos-j][s];
								counter++;
							}
						}
						for(int j = 1; j <= medianBlurRadiusPos && (pos+j) < LUTHeight; j++){
							if(outputCt [al-i][pos+j][s] > 0){
								medianArray [counter] = output [al-i][pos+j][s];
								counter++;
							}
						}
												
					}
					
					for(int i = 1; i <= medianBlurRadiusAl && (al+i) < (int)Math.round(maxArcL*maxResolution)+1; i++){
						if(outputCt [al+i][pos][s] > 0){
							medianArray [counter] = output [al+i][pos][s];
							counter++;
						}			
						for(int j = 1; j <= medianBlurRadiusPos && (pos-j) > 0; j++){
							if(outputCt [al+i][pos-j][s] > 0){
								medianArray [counter] = output [al+i][pos-j][s];
								counter++;
							}
						}
						for(int j = 1; j <= medianBlurRadiusPos && (pos+j) < LUTHeight; j++){
							if(outputCt [al+i][pos+j][s] > 0){
								medianArray [counter] = output [al+i][pos+j][s];
								counter++;
							}
						}
					}
					
					if(counter > 1){
						output2 [al][pos][s] = getMedian(medianArray, counter);
					}
				}
			}
		}		
		
		//find min and max
		for(int s = 0; s < 4; s++){		
			for(int al = 0; al < (int)Math.round(maxArcL*maxResolution)+1; al++){
				for(int pos = 0; pos < LUTHeight; pos++){
					if(output2 [al][pos][s] != 0.0 && output2 [al][pos][s] < minWidthAll)	minWidthAll = output2 [al][pos][s];
					if(output2 [al][pos][s] != 0.0 && output2 [al][pos][s] > maxWidthAll)	maxWidthAll = output2 [al][pos][s];	
				}
			}
		}
		
		//save output image		
		for(int s = 0; s < 4; s++){		
			for(int al = 0; al < (int)Math.round(maxArcL*maxResolution)+1; al++){
				for(int pos = 0; pos < LUTHeight; pos++){		
					if(output2 [al][pos][s] != 0.0){
						outputImp.getStack().setVoxel(al, pos, s, widthToIntensity(minWidthAll, maxWidthAll, output2 [al][pos][s]));
					}					
				}
			}
		}
		
		Date saveDate = new Date(); 
		IJ.saveAsTiff(outputImp, dir[0]+"SpermQ-MF_LUT_" + shortDate.format(saveDate) + ".tif");
		outputImp.changes = false;
		outputImp.close();
		
		//save image metadata
		TextPanel tp = new TextPanel("Metadata");
		tp.append("Image information for merged LUT-image:	SpermQ-MF_LUT_" + shortDate.format(saveDate) + "_info.txt");
		tp.append("Settings:	median blur radius (arclength) =	" + dformat0.format(medianBlurRadiusAl) 
							+ "	median blur radius (z position) =	" + dformat0.format(medianBlurRadiusPos)
							+ "	(+/-)-position distance to find minimum =	" + dformat0.format(minimumFindingRadius));							
		tp.append("dimension		minimum	maximum");
		tp.append("x axis:	xy arc length	0.0	" + dformat6.format(maxArcL) + "	scaling: " + dformat6.format(maxResolution) + "x");
		tp.append("y axis:	zPosition	0	" + dformat0.format(LUTHeight-1) + "	center position (0):	" + LUTRadius);
		tp.append("z axis:	slice	0	3");
		tp.append("gray value (1.0-254.0):	xy gauss fit width	" + dformat6.format(minWidthAll) + "	" + dformat6.format(maxWidthAll));
		tp.append("");
		tp.append("code#"+minWidthAll+"-"+maxWidthAll+"+"+maxResolution+"c"+LUTRadius);
		tp.append("Images included:");
		tp.append("	directory	name	min width	max width	resolution");		
		for(int task = 0; task < tasks; task++){
			tp.append("	" + dir[task] + "	" + name[task]
					+ "	" + minWidth [task]
					+ "	" + maxWidth [task]
					+ "	" + resolution [task]);
		}
		
	  	tp.saveAs(dir[0] + "SpermQ-MF_LUT_" + shortDate.format(saveDate) + "_info.txt");
	  	
	  	progress.setBar(1.0);	  
	  	progress.replaceBarText("done!");
		done = true;
	}
	
	private double widthToIntensity (double min, double max, double intensity){
		//get real width as value:
//		double width = getWidthFromIntensity(min, max, intensity);
		//align to new min and max:
		if(intensity>max)IJ.log("problem intensity > max: " + intensity + ">" + max);
		return 1.0 + 253.0 * ((intensity - min) / (max-min));
	}
	
	private double getWidthFromIntensity (double min, double max, double intensity){
		return min + ((intensity-1.0)/253.0) * (max-min);
	}
	
	public static double getMedian(double [] values, int nrOfValues){
		double [] medians = new double [values.length];
		for(int i = 0; i < values.length; i++){
			medians [i] = values [i];
		}
		
		Arrays.sort(medians);
		
		if(nrOfValues%2==0){
			return (medians[(int)((double)(nrOfValues)/2.0)-1]+medians[(int)((double)(nrOfValues)/2.0)])/2.0;
		}else{
			return medians[(int)((double)(nrOfValues)/2.0)];
		}		
	}
	
	public static double mathAbs(double value){
		return Math.sqrt(Math.pow(value, 2.0));
	}
}