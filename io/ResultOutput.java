package edu.oregonstate.io;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import edu.oregonstate.classifier.Parameter;
import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.features.Feature;
import edu.oregonstate.features.FeatureFactory;
import edu.oregonstate.general.DoubleOperation;
import edu.oregonstate.general.FixedSizePriorityQueue;
import edu.oregonstate.score.CoNLLScorerHelper;
import edu.oregonstate.search.State;
import edu.oregonstate.util.EecbConstants;
import edu.oregonstate.util.EecbConstructor;
import edu.stanford.nlp.dcoref.CorefCluster;
import edu.stanford.nlp.dcoref.CorefScorer;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.Mention;
import edu.stanford.nlp.dcoref.SieveCoreferenceSystem;
import edu.stanford.nlp.dcoref.CorefScorer.ScoreType;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import Jama.Matrix;

public class ResultOutput {

	// write the string to file
	public static void writeTextFile(String fileName, String s) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(fileName, true));
			out.write(s);
			out.newLine();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	// write the string to file
	public static void writeTextFilewithoutNewline(String fileName, String s) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(fileName, true));
			out.write(s);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	/** print the current time in order to know the duration of the experiment */
	public static void printTime(String logPath, String phase) {
		String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-");
		ResultOutput.writeTextFile(logPath, phase + "\n" + timeStamp);
		ResultOutput.writeTextFile(logPath, "\n\n");
	}

	/** get all the sub-directories under the specific directory */
	public static String[] getTopics(String corpusPath) {
		ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, corpusPath);
		File corpusDir = new File(corpusPath);
		String[] directories = corpusDir.list();

		// sort the arrays in order to execute in directory sequence
		// sort string array and sort int array are different.
		// Hence, I need to convert the string array to int array first, and then transform back
		int[] dirs = new int[directories.length];
		for (int i = 0; i < directories.length; i++) {
			dirs[i] = Integer.parseInt(directories[i]);
		}
		Arrays.sort(dirs);
		for (int i = 0; i < directories.length; i++) {
			directories[i] = Integer.toString(dirs[i]);
		}
		return directories;
	}

	/** print the JAMA matrix */
	public static String printModel(Matrix model, String[] featureName) {
		StringBuilder sb = new StringBuilder();
		sb.append("bias weight: " + model.get(0, 0) + "\n");
		for (int i = 0; i < featureName.length; i++) {
			sb.append(featureName[i] + " weight: " + model.get(i+1, 0) + "\n");
		}
		return sb.toString();
	}

	public static String printStructredModel(double[] model, List<String> featureName) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < featureName.size(); i++) {
			sb.append(featureName.get(i) + "\t" + model[i] + "\n");
		}
		return sb.toString().trim();
	}

	/** print the JAMA matrix */
	public static String printModel(Matrix model) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < model.getRowDimension(); i++) {
			sb.append("weight: " + model.get(i, 0) + "\n");
		}
		return sb.toString().trim();
	}

	// delete the intermediate result in case of wrong linear model
	// and also delete the whole directory
	public static void deleteResult(String directoryName) {
		File directory = new File(directoryName);
		File[] files = directory.listFiles();
		if (files == null) {
			return;
		} else if (files.length > 0) {
			for (File file : files) {
				if (!file.delete()) {
					System.out.println("Failed to delete "+file);
				}
			}
		}

		boolean delete = directory.delete();
		assert delete == true;
	}

	/** just delete the file according to the filePath  */
	public static void deleteFile(String filePath) {
		File file = new File(filePath);
		boolean success = file.delete();
		assert success == true;
	}

	public static <T> void serialize(T object, Object id, String directory) {
		try {
			FileOutputStream fileOut = new FileOutputStream(directory + "/" + id +".ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(object);
			out.close();
			fileOut.close();
		} catch (IOException i) {
			i.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T deserialize(String fileName, String directory, boolean delete) {
		T cluster = null;
		try
		{
			FileInputStream fileIn = new FileInputStream(directory + "/" +  fileName + ".ser");
			ObjectInputStream in = new ObjectInputStream(fileIn);
			cluster = (T) in.readObject();
			in.close();
			fileIn.close();
		}catch(IOException i) {
			i.printStackTrace(); 
		}catch(ClassNotFoundException c)
		{
			c.printStackTrace();
			System.exit(1);
		}
		if (delete) {
			deleteFile(directory + "/" + fileName + ".ser");
		}

		return cluster;
	}

	/**
	 * print the cluster information
	 * 
	 * @param clusters
	 * @return
	 */
	public static String printCluster(Map<Integer, CorefCluster> clusters) {
		StringBuilder sb = new StringBuilder();
		for (Integer key : clusters.keySet()) {
			CorefCluster cluster = clusters.get(key);
			sb.append(Integer.toString(key) + "[ ");
			for (Mention mention : cluster.getCorefMentions()) {
				sb.append(mention.mentionID + " : " + mention.spanToString() + "; ");
			}
			sb.append(" ]");
			sb.append("\n");
		}

		return sb.toString();
	}

	/** print the current time in order to know the duration of the experiment */
	public static void printTime() {
		String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-");
		ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, timeStamp);
		ResultOutput.writeTextFile(ExperimentConstructor.experimentLogFile, "\n\n");
	}

	/** print the beam information, because beam is represented as priority queue, we just print the id */
	public static String printBeam(FixedSizePriorityQueue<State<CorefCluster>> beam) {
		List<State<CorefCluster>> elements = beam.getElements();
		double[] priorities = beam.getPriorities();

		assert elements.size() == priorities.length;
		StringBuilder sb = new StringBuilder();
		sb.append("beam: \n");
		for (int i = 0; i < elements.size(); i++) {
			State<CorefCluster> state = elements.get(i);
			double priority = priorities[i];
			sb.append(priority + " " + state.toString() + "\n");
		}
		return sb.toString();
	}

	/**
	 * print debug information for topic
	 * 
	 * @param document
	 * @param topic
	 */
	public static void printParameters(Document document, String topic, String logFile) {
		ResultOutput.writeTextFile(logFile, "Number of Gold Mentions of " + topic +  " : " + document.allGoldMentions.size());
		ResultOutput.writeTextFile(logFile, "Number of predicted Mentions of " + topic +  " : " + document.allPredictedMentions.size());
		ResultOutput.writeTextFile(logFile, "Number of gold clusters of " + topic + " : " + document.goldCorefClusters.size());
		//ResultOutput.writeTextFile(logFile, "Gold clusters : \n" + ResultOutput.printCluster(document.goldCorefClusters));
		ResultOutput.writeTextFile(logFile, "Number of coref clusters of " + topic + " : " + document.corefClusters.size());
		//ResultOutput.writeTextFile(logFile, "Coref Clusters: \n" + ResultOutput.printCluster(document.corefClusters));
	}

	/**
	 * print the document feature for each cluster
	 * 
	 * @param document
	 * @param logFile
	 */
	public static void printClusterFeatures(Document document, String logFile, int searchStep) {
		ResultOutput.writeTextFile(logFile, "features for search step : " + searchStep + " on document " + document.getID() + " with cluster size " + document.corefClusters.size());

		for (Integer key : document.corefClusters.keySet()) {
			CorefCluster cluster = document.corefClusters.get(key);
			StringBuilder sb = new StringBuilder();
			sb.append(Integer.toString(key) + "[ ");
			for (Mention mention : cluster.getCorefMentions()) {
				sb.append(mention.mentionID + " : " + mention.spanToString() + "; ");
			}
			sb.append(" ]");

			sb.append(" ; features :  " + cluster.predictedCentroid);
			ResultOutput.writeTextFile(logFile, sb.toString());
		}


	}

	/**
	 * print document score information
	 * 
	 * @param document
	 * @param logInformation
	 * @param logPath
	 * @param logger
	 */
	//	public static void printDocumentScoreInformation(Document document, String logInformation, String logPath, Logger logger) {
	//		CorefScorer score = EecbConstructor.createCorefScorer(ScoreType.valueOf(ExperimentConstructor.lossScoreType));
	//		score.calculateScore(document);
	//		ResultOutput.writeTextFile(logPath, logInformation);
	//		score.printF1(logger, true);
	//	}

	/** print the local score */
	public static void printScoreInformation(double[] localScores, ScoreType mtype, String logFile) {
		assert localScores.length == 3;
		ResultOutput.writeTextFile(logFile, "local" + mtype.toString() + " F1 Score: " + Double.toString(localScores[0]));
		ResultOutput.writeTextFile(logFile, "local" + mtype.toString() + " precision Score: " + Double.toString(localScores[1]));
		ResultOutput.writeTextFile(logFile, "local" + mtype.toString() + " recall Score: " + Double.toString(localScores[2]));
	}

	/**
	 * print Parameter
	 * 
	 * @param para
	 */
	public static void printParameter(Parameter para, String logFile) {
		int violations = para.getNoOfViolation();
		double[] weight = para.getWeight();
		double[] totalWeight = para.getTotalWeight();
		double[] averageWeight;
		if (violations == 0) {
			averageWeight = new double[weight.length];
		} else {
			double[] copyTotalWeight = new double[weight.length];
			System.arraycopy(totalWeight, 0, copyTotalWeight, 0, weight.length);
			averageWeight = DoubleOperation.divide(copyTotalWeight, violations);
		}

		ResultOutput.writeTextFile(logFile, "the number of total constraints : " + violations);
		ResultOutput.writeTextFile(logFile, "weight vector : " + DoubleOperation.printArray(weight));
		ResultOutput.writeTextFile(logFile, "total weight vector : " + DoubleOperation.printArray(totalWeight));
		ResultOutput.writeTextFile(logFile, "average weight vector : " + DoubleOperation.printArray(averageWeight));
		ResultOutput.writeTextFile(logFile, "total weight vector : " + DoubleOperation.printArray(totalWeight));
	}

	/**
	 * calculate document score according to the loss type
	 * 
	 * @param document
	 * @param type
	 * @return
	 */
	public static String[] printDocumentScore(Document document, ScoreType type, String logFile, String phase) {
		CorefScorer scorer = EecbConstructor.createCorefScorer(type);
		scorer.calculateScore(document);

		NumberFormat nf = new DecimalFormat("0.00");
		double r =scorer.getRecall() * 100;
		double p = scorer.getPrecision() * 100;
		double f1 = scorer.getF1() * 100;

		String R = nf.format(r);
		String P = nf.format(p);
		String F1 = nf.format(f1);

		String str = "F1 = "+F1+", P = "+P+" ("+(int) scorer.precisionNumSum+"/"+(int) scorer.precisionDenSum+"), R = "
				+R+" ("+(int) scorer.recallNumSum+"/"+(int) scorer.recallDenSum+")";
		writeTextFile(logFile, "\nthe overall testing " + type.toString() + " accuracy on " + phase + " set is " +  str + "\n");

		String[] scores = new String[]{F1, P, R};

		return scores;
	}

	/**
	 * calculate document score according to four loss types, respectively Pairwise, MUC, Bcubed, CEAF
	 * 
	 * @param document
	 * @param logFile
	 * @param phase
	 */
	public static List<String[]> printDocumentScore(Document document, String logFile, String phase) {
		List<String[]> scores = new ArrayList<String[]>();
		for (ScoreType type : ScoreType.values()) {
			String[] scoreInformation = printDocumentScore(document, type, logFile, phase);
			scores.add(scoreInformation);
		}

		return scores;
	}

	/**
	 * print document results to file which is used for CoNLL scoring
	 * 
	 * @param document : the current document object
	 * @param goldCorefCluster : gold coref cluster result file
	 * @param predictedCorefCluster : predicted coref cluster result file
	 * @param postProcess : whether post-process the result
	 */
	public static void printDocumentResultToFile(Document document, String goldCorefCluster, String predictedCorefCluster) {
		boolean postProcess = ExperimentConstructor.postProcess;
		PrintWriter writerPredicted = null;
		PrintWriter writerGold = null;
		try {
			writerPredicted = new PrintWriter(new FileOutputStream(predictedCorefCluster, true));
			writerGold = new PrintWriter(new FileOutputStream(goldCorefCluster, true));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		SieveCoreferenceSystem.printConllOutput(document, writerPredicted, false, postProcess);
		boolean postProcessGold = Boolean.parseBoolean(ExperimentConstructor.experimentProps.getProperty(EecbConstants.DATAGENERATION_POSTPROCESS_GOLD_PROP, "false"));
		if (postProcessGold) {
			SieveCoreferenceSystem.printConllOutput(document, writerGold, true, postProcess);
		} else {
			SieveCoreferenceSystem.printConllOutput(document, writerGold, true);
		}

		writerPredicted.close();
		writerPredicted.close();
	}

	/**
	 * print the final conll result
	 * 
	 * @param index : the current epoch
	 * @param logFile : log file used to store the intermediate result
	 * @param goldCorefCluster : gold coref cluster file
	 * @param predictedCorefCluster : predicted coref cluster file
	 * @param phase : used to indicate which phase the current process is, just appear in the result instead of as part of the folder name
	 */
	public static double[] printCorpusResult(String logFile, String goldCorefCluster, String predictedCorefCluster, String phase) {
		CoNLLScorerHelper conllScorerHelper = new CoNLLScorerHelper(logFile);
		double[] finalScores = conllScorerHelper.printFinalCoNLLScore(goldCorefCluster, predictedCorefCluster, phase);

		return finalScores;
	}

	/**
	 * print mention information for the orderedMention
	 * 
	 * @param orderedMentions
	 * @param logFile
	 */
	public static void printMentionsInformation(List<List<Mention>> orderedMentions, String logFile) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < orderedMentions.size(); i++ ) {
			List<Mention> mentions = orderedMentions.get(i);
			for (int j = 0; j < mentions.size(); j++) {
				Mention mention = mentions.get(j);
				sb.append(mention.mentionID + " : " + mention.spanToString() + "\n");
			}
		}

		writeTextFile(logFile, sb.toString().trim());
	}

	/**
	 * read file based on the file path
	 * 
	 * @param path
	 * @return
	 */
	public static List<String> readFiles(String path) {
		List<String> weights = new ArrayList<String>();

		try {
			String sCurrentLine;
			BufferedReader br = new BufferedReader(new FileReader(path));
			while((sCurrentLine = br.readLine()) != null) {
				weights.add(sCurrentLine);
			}

			br.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		return weights;
	}
	
	public static Map<String, String> readFiles(String path, String separator) {
		List<String> records = readFiles(path);
		Map<String, String> datas = new HashMap<String, String>();
		for (String record : records) {
			String[] elements = record.split(separator);
			if (elements.length == 1) {
				datas.put(elements[0], "0.0");
			} else {
				datas.put(elements[0], elements[1]);
			}
		}
		
		return datas;
	}

	/**
	 * build feature string for the predictredCentroid
	 * 
	 * @param predictedCentroid
	 * @return
	 */
	public static String buildCounterFeatureString(HashMap<String, ClassicCounter<String>> predictedCentroid ) {

		StringBuffer sb = new StringBuffer();
		sb.append("[");
		for (String feature : predictedCentroid.keySet()) {
			ClassicCounter<String> value = predictedCentroid.get(feature);
			if ((value != null) && (value.size() > 0)) {
				sb.append( feature + " : " + value.toString() + ", ");
			}
		}
		
		sb.append("]");
		return sb.toString().trim();
		
	}
	
	/**
	 * print properties file
	 * 
	 * @param props
	 * @param logFile
	 */
	public static void printProperties(Properties props, String logFile) {
        Enumeration keys = props.keys();
        while (keys.hasMoreElements()) {
          String key = (String)keys.nextElement();
          String value = (String)props.get(key);
          ResultOutput.writeTextFile(logFile ,key + " : " + value);
        }
	}
}
