package edu.oregonstate.training;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import edu.stanford.nlp.dcoref.Mention;
import edu.oregonstate.CDCR;
import edu.oregonstate.CorefSystem;
import edu.oregonstate.classifier.LinearRegression;
import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.features.Feature;
import edu.oregonstate.io.ResultOutput;
import edu.oregonstate.search.IterativeResolution;
import edu.oregonstate.util.EecbConstants;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.stats.Counter;
import Jama.Matrix;

/**
 * train the linear regression Co-reference model
 * 
 * <p>
 * The specification of the whole process is shown in the algorithm 2 of the paper
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class Train {

	//TODO
	// clusters results, right now, I just use the original corpus
	// Later time, I will use clustering result using EM variant where the 
	// the initial points (and the number of clusters) are selected from the clusters
	// generated by a hierarchical agglomerative clustering algorithm using geometric heuristics
	private String[] mTrainingTopics;
	private int mIteration;
	private double mCoefficient;
	private double mLamda;
	private String[] outputFileNames = {"one.csv", "two.csv", "three.csv", "four.csv", "five.csv", "six.csv", "seven.csv", "eight.csv", "nine.csv", "ten.csv", "initial.csv"};
	public static String currentOutputFileName = "";
	
	/**
	 * Train constructor
	 * 
	 * @param trainingTopics
	 */
	public Train( String[] trainingTopics) {
		mTrainingTopics = trainingTopics;
		mIteration = (Integer) ExperimentConstructor.getParameter(EecbConstants.CLASSIFIER, "noOfIteration");
		mCoefficient = (Double) ExperimentConstructor.getParameter(EecbConstants.CLASSIFIER, "coefficient");
		mLamda = (Double) ExperimentConstructor.getParameter(EecbConstants.CLASSIFIER, "interPolationWeight");
	}
	
	/** main method for training the linear regression model, in the current time, use four roles now */
	public Matrix train(Matrix initialWeight) {
		Matrix model = initialWeight;
		// training data for linear regression
		for (int i = 0; i < mIteration; i++) {
			ResultOutput.writeTextFile(ExperimentConstructor.logFile, "Start train the model: "+ i +"th iteration ============================================================");
			currentOutputFileName = ExperimentConstructor.linearRegressionTrainingPath + "/" + outputFileNames[i];
			/** all mentions in one doc cluster */
			for (String topic : mTrainingTopics) {
				ResultOutput.writeTextFile(ExperimentConstructor.logFile, "Linear regreession begin to process topic " + topic+ "................");
				try {
					ResultOutput.writeTextFile(ExperimentConstructor.logFile, "Starting to do training on " + topic);
					Document document = ResultOutput.deserialize(topic, ExperimentConstructor.serializedOutput, false);
					// before search parameters
					ResultOutput.writeTextFile(ExperimentConstructor.logFile, "topic " + topic + "'s detail before merge");
					ExperimentConstructor.printParameters(document, topic);
					
				    IterativeResolution ir = new IterativeResolution(document, model);
				    ir.merge();
				    
				    // after search parameters
					ResultOutput.writeTextFile(ExperimentConstructor.logFile, "topic " + topic + "'s detail after merge");
					ExperimentConstructor.printParameters(document, topic);
				}catch (Exception e) {
					e.printStackTrace();
					System.exit(1);
				}
				ResultOutput.writeTextFile(ExperimentConstructor.logFile, "Linear regression end to process topic " + topic+ "................");
			}
			
			// <b>NOTE</b>: change this part in order to incorporate all 0 instances
			LinearRegression lr = new LinearRegression(currentOutputFileName, mCoefficient); 
			Matrix updateModel = lr.calculateWeight();
			
			Matrix coupdateModel = updateModel.times(1 - mLamda);
			Matrix comodel = model.times(mLamda);
			model = new Matrix(comodel.getRowDimension(), comodel.getColumnDimension());
			model = comodel.plus(coupdateModel);
			ResultOutput.writeTextFile(ExperimentConstructor.logFile, "Finish train the model: "+ i +"th iteration ===================================================");
			ResultOutput.writeTextFile(ExperimentConstructor.logFile, "the current weight\n" + ResultOutput.printModel(model, Feature.featuresName));
		}
		
		return model;
	}
	
	/**
	 * This procedure runs the high-precision sieves introduced just like the data generation loop in algorithm 2, creates training examples from 
	 * the clusters available after every merge operation. Since these deterministic models address only nominal clusters, at the end we generate 
	 * training data for events by inspecting all the pairs of singleton verbal clusters. Using this data, we train the initial linear regression model.
	 * 
	 * @return the initial weight set
	 */
	public Matrix assignInitialWeights() {		
		LinearRegression lr = new LinearRegression(ExperimentConstructor.linearRegressionTrainingPath + "/initial.csv", mCoefficient);
		Matrix initialModel = lr.calculateWeight();
		ResultOutput.writeTextFile(ExperimentConstructor.logFile, "initial weight: " + ResultOutput.printModel(initialModel, Feature.featuresName));
		ResultOutput.writeTextFile(ExperimentConstructor.logFile, "Finish train the initial model: ===================================================");
		return initialModel;
	}
	
}
