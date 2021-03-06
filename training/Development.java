package edu.oregonstate.training;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Properties;

import edu.oregonstate.dataset.CorefSystem;
import edu.oregonstate.dataset.TopicGeneration;
import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.general.DoubleOperation;
import edu.oregonstate.io.ResultOutput;
import edu.oregonstate.search.ISearch;
import edu.oregonstate.search.State;
import edu.oregonstate.util.Command;
import edu.oregonstate.util.DocumentAlignment;
import edu.oregonstate.util.EecbConstants;
import edu.oregonstate.util.EecbConstructor;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.CorefScorer.ScoreType;
import edu.stanford.nlp.dcoref.Document;

/**
 * Development Set used to tune the hyper-parameters. In our experiment, we need 
 * to tune the stopping rate for the testing phase.
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class Development {
	
	/* experiment properties */
	private final Properties mProps;
	
	/** development topics */
	private final String[] mDevelopmentTopics;

	/** current epoch */
	private final int mCurrentEpoch;
	
	/** search method */
	private final String searchMethod;
	
	/** the weight used for validation */
	private final double[] mLearnedWeight;
	
	/** starting digit */
	private final double mStartNumber;
	
	/** ending digit */
	private final double mEndNumber;
	
	/** iterations */
	private final int mIterations;
	
	/* log file */
	private final String logFile;
	
	/* conll result folder */
	private final String conllResultPath;
	
	/* serialized output */
	private final String serializeOutput;
	
	/* experiment result folder */
	private final String experimentResultFolder;
	
	/* whether post-process the development set */
	private final boolean postProcess;
	
	/* loss score type */
	private final ScoreType lossScoreType;
	
	/**
	 * 
	 * @param developmentTopics 
	 * @param currentEpoch
	 * @param learnedWeight
	 * @param startNumber : the number used for indicating the beginning number for validation
	 * @param endNumber : the number used for indicating the end number for validation
	 */
	public Development(int currentEpoch, double[] learnedWeight, double startNumber, double endNumber, int iterations) {
		mProps = ExperimentConstructor.experimentProps;
		TopicGeneration topicGenerator = new TopicGeneration(ExperimentConstructor.experimentProps);
		mDevelopmentTopics = topicGenerator.developmentTopics();
		experimentResultFolder = ExperimentConstructor.experimentFolder;
		conllResultPath = experimentResultFolder + "/conll";
		serializeOutput = experimentResultFolder + "/document";
		
		mCurrentEpoch = currentEpoch;
		searchMethod = mProps.getProperty(EecbConstants.SEARCH_METHOD, "BeamSearch");
		mLearnedWeight = learnedWeight;
		mStartNumber = startNumber;
		mEndNumber = endNumber;
		mIterations = iterations;
		logFile = ExperimentConstructor.experimentLogFile;
		
		postProcess = ExperimentConstructor.postProcess;
		lossScoreType = ScoreType.valueOf(mProps.getProperty(EecbConstants.LOSSFUNCTION_SCORE_PROP, "Pairwise"));
	}
	
	/**
	 * tuning the parameter
	 */
	public double tuning() {
		double[] stoppingRates = DoubleOperation.createDescendingArray(mStartNumber, mEndNumber, mIterations);
		boolean bestStateScore = Boolean.parseBoolean(mProps.getProperty(EecbConstants.SEARCH_BESTSTATE, "true"));

		ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, "\nBegin Tuning parameter for the model in the " + mCurrentEpoch + "th iteration\n");

		double maximumScore = 0.0;
		double optimizedStoppingRate = 0.0;
		// do tuning
		for (double stoppingRate : stoppingRates) {
			Document corpus = new Document();
			corpus.goldCorefClusters = new HashMap<Integer, CorefCluster>();
			
			ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, "\nstopping rate number : " + stoppingRate + " for the "  + mCurrentEpoch + "th iteration\n");
			
			String goldCorefCluster = conllResultPath + "/goldCorefCluster-tuning-" + mCurrentEpoch + "-" + stoppingRate;
			String predictedCorefCluster = conllResultPath + "/predictedCorefCluster-tuning-" + mCurrentEpoch + "-" + stoppingRate;

			PrintWriter writerPredicted = null;
			PrintWriter writerGold = null;
			try {
				writerPredicted = new PrintWriter(new FileOutputStream(predictedCorefCluster));
				writerGold = new PrintWriter(new FileOutputStream(goldCorefCluster));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			String phaseID = mCurrentEpoch + "-" + stoppingRate;

			for (String topic : mDevelopmentTopics) {
				ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, "\nStarting to tuning on " + topic + " with stpping rate " + stoppingRate + " for the " + mCurrentEpoch + "th iteration\n");
				Document document = ResultOutput.deserialize(topic, serializeOutput, false);

				// before search : document parameters
				ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, "topic " + topic + "'s detail before search during tuning-" + mCurrentEpoch + "-" + stoppingRate);
				ResultOutput.printParameters(document, topic, logFile);

				// configure dynamic file and folder path
				String currentExperimentFolder = experimentResultFolder + "/" + topic;
				Command.mkdir(currentExperimentFolder);
				
				ISearch search = EecbConstructor.createSearchMethod(searchMethod);
				State<CorefCluster> bestLossState = search.testingBySearch(document, mLearnedWeight, phaseID, false, stoppingRate);
				
				if (bestStateScore) {
					document.corefClusters = bestLossState.getState();
				}
				
				DocumentAlignment.alignDocument(document);

				// do pronoun coreference resolution
				CorefSystem cs = new CorefSystem();
				cs.applyPronounSieve(document);

				// whether post-process the document
				if (postProcess) {
					DocumentAlignment.postProcessDocument(document);
				}

				// add single document to the corpus
				ResultOutput.printDocumentScore(document, lossScoreType, logFile, "single " + phaseID + " document " + topic);
				ResultOutput.printParameters(document, topic, logFile);
				DocumentAlignment.mergeDocument(document, corpus);
				
				ResultOutput.printDocumentResultToFile(document, goldCorefCluster, predictedCorefCluster);
			}

			writerPredicted.close();
			writerGold.close();
			
			// do scoring on this iteration
			try {
				// Pairwise
				String[] scoreInformation = ResultOutput.printDocumentScore(corpus, lossScoreType, logFile, phaseID);

				// CoNLL scoring
				double[] finalScores = ResultOutput.printCorpusResult(logFile, goldCorefCluster, predictedCorefCluster, "model generation");
				ResultOutput.writeTextFile(experimentResultFolder + "/tuning/" + phaseID + ".csv", scoreInformation[0] + "\t" + finalScores[0] + "\t" + 
												finalScores[1] + "\t" + finalScores[2] + "\t" + finalScores[3] + "\t" + finalScores[4]);
				
				double score = finalScores[4];
				if (score > maximumScore) {
					optimizedStoppingRate = stoppingRate;
					maximumScore = score;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, "the stopping rate : " + optimizedStoppingRate);
		return optimizedStoppingRate;
	}
	
}