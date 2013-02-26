package edu.oregonstate.method;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import edu.oregonstate.classifier.IClassifier;
import edu.oregonstate.classifier.Parameter;
import edu.oregonstate.dataset.CorefSystem;
import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.features.FeatureFactory;
import edu.oregonstate.general.DoubleOperation;
import edu.oregonstate.io.ResultOutput;
import edu.oregonstate.search.ISearch;
import edu.oregonstate.search.State;
import edu.oregonstate.training.Development;
import edu.oregonstate.util.Command;
import edu.oregonstate.util.DocumentAlignment;
import edu.oregonstate.util.EecbConstants;
import edu.oregonstate.util.EecbConstructor;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.CorefScorer.ScoreType;

/**
 * use Dagger method to train the whole experiment
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class Dagger implements IMethod {

	/* experiment configuration */
	private final Properties mProps;

	/* method epoch */
	private final int methodEpoch;

	/* number of member functions */
	private final int numberOfFunctions;

	/* training topics */
	private final String[] trainingTopics;

	/* testing topics */
	private final String[] testingTopics;

	/* search method */
	private final String searchMethod;

	/* serialized output */
	private final String serializeOutput;

	/* classification method */
	private final String classificationMethod;

	/* conll result folder */
	private final String conllResultPath;

	/* log File */
	private final String logFile;

	/* experiment result */
	private final String experimentResultFolder;

	/* loss type */
	private final ScoreType lossType;
	
	/* enable best search state score */
	private final boolean bestStateScore;

	public Dagger() {
		mProps = ExperimentConstructor.experimentProps;
		experimentResultFolder = ExperimentConstructor.experimentResultFolder;
		methodEpoch = Integer.parseInt(mProps.getProperty(EecbConstants.METHOD_EPOCH_PROP, "1"));
		logFile = ExperimentConstructor.logFile;
		numberOfFunctions = Integer.parseInt(mProps.getProperty(EecbConstants.METHOD_FUNCTION_NUMBER_PROP, "3"));
		trainingTopics = ExperimentConstructor.trainingTopics;
		testingTopics = ExperimentConstructor.testingTopics;
		searchMethod = mProps.getProperty(EecbConstants.SEARCH_PROP, "BeamSearch");
		serializeOutput = experimentResultFolder + "/documentobject";
		classificationMethod = mProps.getProperty(EecbConstants.CLASSIFIER_PROP, "StructuredPerceptron");
		conllResultPath = experimentResultFolder + "/conllResult";
		lossType = ScoreType.valueOf(mProps.getProperty(EecbConstants.LOSSFUNCTION_SCORE_PROP, "Pairwise"));
		bestStateScore = Boolean.parseBoolean(mProps.getProperty(EecbConstants.ENABLE_BEST_SEARCH_SCORE, "false"));
	}

	/**
	 * Learn the final weight, which can be used for 
	 */
	public List<Parameter> executeMethod() {
		int length = FeatureFactory.getFeatureTemplate().size();
		List<Parameter> paras = new ArrayList<Parameter>();
		double[] weight = new double[length];
		double[][] variance = DoubleOperation.generateIdentityMatrix(length);
		Parameter para = new Parameter(weight, variance);

		// in the current time, just 1 epoch
		for (int i = 1; i <= methodEpoch; i++) {
			// 0: the true loss function
			// 1- numberOfFunctions : the learned function
			for (int j = 1; j <= numberOfFunctions; j++) {
				// training
				ResultOutput.writeTextFile(logFile, "\n\n(Dagger) Training Iteration Epoch : " + i + "; Training Model : " + j + "\n\n");
				ResultOutput.printParameter(para, logFile);
				para = trainModel(para, i, j);

				//testing
				ResultOutput.writeTextFile(logFile, "\n\n(Dagger) Testing Iteration Epoch : " + i + "; Testing Model : " + j + "\n\n");
				ResultOutput.printParameter(para, logFile);
				testModel(generateWeightForTesting(para), i, j);

				// add returned parameter to the final parameters
				paras.add(para.makeCopy());
			}
		}

		assert paras.size() == numberOfFunctions;
		return paras;
	}

	/**
	 * train the model
	 * 
	 * @param para
	 * @param j
	 * @param i
	 * @param phaseID
	 * @return
	 */
	private Parameter trainModel(Parameter para, int i, int j) {
		ISearch search = EecbConstructor.createSearchMethod(searchMethod);
		String phase = j * 1000 + "";
		boolean trainPostProcess = Boolean.parseBoolean(mProps.getProperty(EecbConstants.TRAIN_POSTPROCESS_PROP, "false"));
		// generate training data for classification
		if (j == 1) {
			Document corpus = new Document();
			corpus.goldCorefClusters = new HashMap<Integer, CorefCluster>();

			String goldCorefCluster = conllResultPath + "/goldCorefCluster-training" + "-" + i + "-" + j;
			String predictedCorefCluster = conllResultPath + "/predictedCorefCluster-training" + "-" + i + "-" + j;

			for (String topic : trainingTopics) {
				ResultOutput.writeTextFile(logFile, "\n(Dagger) Training Iteration Epoch : " + i + "; Training Model : " + j + "; Document : " + topic + "\n");
				Document document = ResultOutput.deserialize(topic, serializeOutput, false);

				// create training data directory
				String trainingDataPath = experimentResultFolder + "/" + document.getID() + "/data";
				Command.createDirectory(trainingDataPath);

				// conduct search using the true loss function
				search.trainingBySearch(document, para, phase);
				DocumentAlignment.alignDocument(document);
			
				// apply the pronoun sieve
				CorefSystem cs = new CorefSystem();
				cs.applyPronounSieve(document);

				// whether post-process the document
				if (trainPostProcess) {
					DocumentAlignment.postProcessDocument(document);
				}
				
				ResultOutput.printDocumentScore(document, lossType, logFile, "single training" + " document " + topic);
				ResultOutput.printDocumentResultToFile(document, goldCorefCluster, predictedCorefCluster, trainPostProcess);
				DocumentAlignment.mergeDocument(document, corpus);
			}

			// Stanford scoring
			String[] scoreInformation = ResultOutput.printDocumentScore(corpus, lossType, logFile, "training-with-true-loss-function");

			// CoNLL scoring
			double[] finalScores = ResultOutput.printCorpusResult(j, logFile, goldCorefCluster, predictedCorefCluster, "model generation");
			ResultOutput.writeTextFile(experimentResultFolder + "/trainingset.csv", scoreInformation[0] + "\t" + finalScores[0] + "\t" + finalScores[1] + "\t" + finalScores[2] + "\t" + finalScores[3] + "\t" + finalScores[4]);

		} else {

			// use average model to collect more data
			testDocument(trainingTopics, generateWeightForTesting(para), i, j, trainPostProcess, "trainingset", true, 0.0);
		}

		// train the model using the specified classifier for several iterations, using small learning rate
		IClassifier classifier = EecbConstructor.createClassifier(classificationMethod);
		List<String> filePaths = getPaths(i, j);
		ResultOutput.writeTextFile(experimentResultFolder + "/searchstep", "" + filePaths.size());
		ResultOutput.writeTextFile(logFile, "the total number of training files : " + filePaths.size());
		Parameter returnPara = classifier.train(filePaths, j);
		return returnPara.makeCopy();
	}

	/**
	 * get the path of training data
	 * 
	 * @param i
	 * @param j
	 * @return
	 */
	private List<String> getPaths(int i, int j) {
		List<String> filePaths = new ArrayList<String>();
		boolean random = false;
		if (random) {
			filePaths.addAll(getPaths(trainingTopics));
		} else {
			filePaths.addAll(getPaths(trainingTopics, i, j));
		}

		return filePaths;
	}

	/**
	 * random get topic from one directory
	 * 
	 * @param topics
	 * @return
	 */
	private List<String> getPaths(String[] topics){
		List<String> allfiles  = new ArrayList<String>();
		for (String topic : topics) {
			String topicPath = experimentResultFolder + "/" + topic + "/data/";
			List<String> files = new ArrayList<String>(Arrays.asList(new File(topicPath).list()));
			List<String> sortedFiles = sort(files);
			List<String> filePaths = new ArrayList<String>();
			for (String file : sortedFiles) {
				filePaths.add(topicPath + file);
			}

			allfiles.addAll(filePaths);
		}

		return allfiles;
	}

	/**
	 * aggregate the training data
	 * 
	 * @param topics
	 * @param i
	 * @param j
	 * @return
	 */
	private List<String> getPaths(String[] topics, int i, int j) {
		List<String> allfiles  = new ArrayList<String>();
		for (int it = 1; it <= i; it++) {
			for (int jt = 1; jt <= j; jt++) {
				for (String topic : topics) {
					List<String> files = getDivisionPaths(topic, it, jt);
					String topicPath = experimentResultFolder + "/" + topic + "/data/";
					List<String> filePaths = new ArrayList<String>();
					for (String file : files) {
						filePaths.add(topicPath + file);
					}

					allfiles.addAll(filePaths);
				}
			}
		}

		return allfiles;
	}

	// get a sequence of data file, such as 1, 2, 3, 4, 5
	private List<String> getDivisionPaths(String topic, int it, int jt) {
		String topicPath = experimentResultFolder + "/" + topic + "/data/";
		List<String> files = new ArrayList<String>(Arrays.asList(new File(topicPath).list()));
		List<String> processedFiles= sortDivide(files, it, jt);

		return processedFiles;
	}

	/**
	 * according to numeric values to sort files
	 * @param files
	 * @return
	 */
	private List<String> sort(List<String> files) {
		Integer[] numbers = new Integer[files.size()];
		for (int i = 0; i < files.size(); i++) {
			numbers[i] = Integer.parseInt(files.get(i));
		}
		Arrays.sort(numbers);
		List<String> outputPath = new ArrayList<String>();
		for (int i = 0; i < numbers.length; i++) {
			outputPath.add(numbers[i] + "");
		}
		return outputPath;
	}

	/**
	 * sort the file
	 * 
	 * @param files
	 */
	private List<String> sortDivide(List<String> files, int it, int jt) {
		Integer[] numbers = new Integer[files.size()];
		for (int i = 0; i < files.size(); i++) {
			numbers[i] = Integer.parseInt(files.get(i));
		}
		Arrays.sort(numbers);
		List<String> divideFile = new ArrayList<String>();
		int constant = jt * 1000;
		for (int i = 0; i < numbers.length; i++) {
			int number = numbers[i];
			if ((number - constant) == 0) {
				divideFile.add(number + "");
			}
		}
		return divideFile;
	}
	
	// tune the stopping rate
	public double tuneStoppingRate(double[] weight, int i, int j) {
		double stoppingrate = 0.0;
		String stopping = mProps.getProperty(EecbConstants.STOPPING_CRITERION);
		if (stopping.equals("tuning")) {
			Development development = new Development(i, j, weight, 1.0, 3.0, 10);
			stoppingrate = development.tuning();
			ResultOutput.writeTextFile(logFile, "\nthe stopping rate is : " + stoppingrate + " for " + i + "-" + j + "\n");
		}
		
		return stoppingrate;
	}

	/**
	 * test the model
	 * 
	 * @param para
	 * @param j
	 * @param i
	 */
	private void testModel(double[] weight, int i, int j) {
		// do not need do testing on training
		boolean testTraining = Boolean.parseBoolean(mProps.getProperty(EecbConstants.TRAINING_VALIDATION_PROP, "false"));
		
		// set stopping rate for tuning
		double stoppingrate = tuneStoppingRate(weight, i, j);

		// do testing on training set
		if (testTraining && (j == numberOfFunctions)) {
			ResultOutput.writeTextFile(logFile, "\n\n(Dagger) testing on training set\n\n");
			boolean trainPostProcess = Boolean.parseBoolean(mProps.getProperty(EecbConstants.TRAIN_POSTPROCESS_PROP, "false"));
			testDocument(trainingTopics, weight, i, numberOfFunctions + 1, trainPostProcess, "trainingset", false, stoppingrate);
		}

		// do testing on testing set
		ResultOutput.writeTextFile(logFile, "\n\n(Dagger) testing on testing set\n\n");
		boolean testPostProcess = Boolean.parseBoolean(mProps.getProperty(EecbConstants.TEST_POSTPROCESS_PROP, "false"));
		testDocument(testingTopics, weight, i, j, testPostProcess, "testingset", false, stoppingrate);
	}

	/**
	 * do testing on the topics
	 * 
	 * @param topics
	 * @param weight
	 * @param i
	 * @param j
	 * @param postProcess
	 * @param phase
	 */
	public void testDocument(String[] topics, double[] weight, int i, int j, boolean postProcess, String phase, boolean outputFeature, double stoppingrate){
		// store the predicted mentions and gold mentions into corpus
		Document corpus = new Document();
		corpus.goldCorefClusters = new HashMap<Integer, CorefCluster>();

		// conll scoring files
		String goldCorefCluster = conllResultPath + "/goldCorefCluster-" + phase + "-" + i + "-" + j;
		String predictedCorefCluster = conllResultPath + "/predictedCorefCluster-" + phase + "-" + i + "-" + j;

		String phaseID = j * 1000 + "";
		for(String topic : topics) {
			ResultOutput.writeTextFile(logFile, "\n\n(Dagger) Testing Iteration Epoch : " + phaseID + "; Document :" + topic + "\n\n");

			Document document = ResultOutput.deserialize(topic, serializeOutput, false);
			ResultOutput.printParameters(document, topic, logFile);

			ISearch search = EecbConstructor.createSearchMethod(searchMethod);
			State<CorefCluster> bestLossState = search.testingBySearch(document, weight, phaseID, outputFeature, stoppingrate);
			
			// if enable best score
			if (bestStateScore) {
				document.corefClusters = bestLossState.getState();
			}
			DocumentAlignment.alignDocument(document);

			// do pronoun coreference resolution
			CorefSystem cs = new CorefSystem();
			cs.applyPronounSieve(document);
			
			// print the cluster result
			//ResultOutput.writeTextFile(logFile, "\ngold clusters\n");
			//ResultOutput.writeTextFile(logFile, ResultOutput.printCluster(document.goldCorefClusters));
			//ResultOutput.writeTextFile(logFile, "\npredicted clusters\n");
			//ResultOutput.writeTextFile(logFile, ResultOutput.printCluster(document.corefClusters));
			
			// whether post-process the document
			if (postProcess) {
				DocumentAlignment.postProcessDocument(document);
			}
			
			//print the cluster result
			//ResultOutput.writeTextFile(logFile, "\n\nafter post-process\n\n");
			//ResultOutput.writeTextFile(logFile, "\ngold clusters\n");
			//ResultOutput.writeTextFile(logFile, ResultOutput.printCluster(document.goldCorefClusters));
			//ResultOutput.writeTextFile(logFile, "\npredicted clusters\n");
			//ResultOutput.writeTextFile(logFile, ResultOutput.printCluster(document.corefClusters));

			// add single document to the corpus
			ResultOutput.printDocumentScore(document, lossType, logFile, "single " + phase + " document " + topic);
			ResultOutput.printParameters(document, topic, logFile);
			
			DocumentAlignment.mergeDocument(document, corpus);
			
			ResultOutput.printDocumentResultToFile(document, goldCorefCluster, predictedCorefCluster, postProcess);
		}

		// Stanford scoring
		String[] scoreInformation = ResultOutput.printDocumentScore(corpus, lossType, logFile, phase);

		// CoNLL scoring
		double[] finalScores = ResultOutput.printCorpusResult(j, logFile, goldCorefCluster, predictedCorefCluster, "model generation");
		ResultOutput.writeTextFile(experimentResultFolder + "/" + phase + ".csv", scoreInformation[0] + "\t" + finalScores[0] + "\t" + 
										finalScores[1] + "\t" + finalScores[2] + "\t" + finalScores[3] + "\t" + finalScores[4]);
	}

	/**
	 * generate weight for testing, average weight or latest weight
	 * 
	 * @param para
	 * @return
	 */
	public double[] generateWeightForTesting(Parameter para) {
		boolean averageWeight = Boolean.parseBoolean(mProps.getProperty(EecbConstants.WEIGHT_PROP, "true"));
		Parameter finalPara = para.makeCopy();
		double[] learnedWeight;
		if (averageWeight) {
			learnedWeight = DoubleOperation.divide(finalPara.getTotalWeight(), finalPara.getNoOfViolation());
		} else {
			learnedWeight = finalPara.getWeight();
		}
		return learnedWeight;
	}

}
