package edu.oregonstate.search;

import java.util.List;
import java.util.Properties;

import edu.oregonstate.classifier.Parameter;
import edu.oregonstate.dataset.TopicGeneration;
import edu.oregonstate.experiment.ExperimentConstructor;
import edu.oregonstate.features.FeatureFactory;
import edu.oregonstate.general.DoubleOperation;
import edu.oregonstate.io.ResultOutput;
import edu.oregonstate.method.CoreferenceResolutionDecoding;
import edu.oregonstate.util.Command;
import edu.oregonstate.util.EecbConstants;
import edu.oregonstate.util.EecbConstructor;
import edu.stanford.nlp.dcoref.Document;
import edu.stanford.nlp.dcoref.CorefScorer.ScoreType;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * Search Entry point
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class SearchFactory extends ExperimentConstructor {

	// model index
	private String phaseIndex;
	
	// experiment name
	private final String experimentName;
	
	/** topic used to do search */
	private final String topicInformation;
	
	/** serialized output */
	private final String serializeOutput;
	
	/** search method */
	private final ISearch search;
	
	/** result folder */
	private final String resultPath;
	
	/** score type */
	private final ScoreType lossType;
	
	/** conll folder */
	private final String conllResultPath;
	
	public SearchFactory(Properties props) {
		super(props);
		
		// experiment name
		experimentName = props.getProperty(EecbConstants.SEARCH_TYPE, "searchtrueloss");
		phaseIndex = props.getProperty(EecbConstants.PHASE_PROP, "0");
		
		TopicGeneration topicGenerator = new TopicGeneration(props);
		topicInformation = topicGenerator.topic();
		
		serializeOutput = ExperimentConstructor.resultPath + "/document";
		resultPath = ExperimentConstructor.resultPath;
		
		// create search
		String searchMethod = props.getProperty(EecbConstants.SEARCH_METHOD, "BeamSearch");
		search = EecbConstructor.createSearchMethod(searchMethod);
		
		lossType = ScoreType.valueOf(props.getProperty(EecbConstants.LOSSFUNCTION_SCORE_PROP, "Pairwise"));
		
		conllResultPath = resultPath + "/conll/" + phaseIndex;
		Command.mkdir(conllResultPath);
	}
	

	/**
	 * perform experiment through search
	 * 
	 */
	public void performExperiment() {
		int length = FeatureFactory.getFeatureTemplate().size();
		String[] element = topicInformation.split("-");
		String topic = element[0];
		String phase = phaseIndex + "-" + element[1] + "-" + topic;
		String topicLogFile = resultPath + "/" + topic + "/logfile";
		
		// do search with true loss function
		if (experimentName.equals("searchtrueloss")) {
			Document document = ResultOutput.deserialize(topic, serializeOutput, false);
			searchwithTrueLoss(document, phase, length);
			
		} else {
			boolean outputFeature = false;
			int modelIndex = Integer.parseInt(phaseIndex);
			if (experimentName.equals("searchlearnedweightwithfeature")) {
				outputFeature = true;
				modelIndex = modelIndex - 1;
			}
			
			String path = resultPath + "/model/model" + modelIndex;
			List<String> para = IOUtils.linesFromFile(path);
			String weightString = para.get(0);
			double[] weight = DoubleOperation.transformString(weightString, ",");
			
			// print the weight out
			ResultOutput.writeTextFile(topicLogFile, "the length of the weight : " + weight.length);
			ResultOutput.writeTextFile(topicLogFile, DoubleOperation.printArray(weight));

			CoreferenceResolutionDecoding decoder = new CoreferenceResolutionDecoding(phase, topic, outputFeature, 0.0, phaseIndex);
			decoder.decode(weight);
		}
		
	}
	
	/**
	 * do search with true loss function
	 * 
	 * @param document
	 * @param phase
	 * @param length
	 */
	private void searchwithTrueLoss(Document document, String phase, int length) {
		String topic = document.getID();
		
		String goldCorefCluster = conllResultPath + "/goldCorefCluster-" + phase;
		String predictedCorefCluster = conllResultPath + "/predictedCorefCluster-" + phase;
		String experimentLogFile = resultPath + "/" + topic + "/logfile";
		
		double[] weight = new double[length];
		Parameter para = new Parameter(weight);
	
		String trainingDataPath = resultPath + "/" + topic + "/data";
		Command.mkdir(trainingDataPath);
		
		// conduct search using the true loss function
		search.trainingBySearch(document, para, phase);
		
		ResultOutput.printDocumentScore(document, lossType, experimentLogFile, "single training" + " document " + topic);
		ResultOutput.printDocumentResultToFile(document, goldCorefCluster, predictedCorefCluster);
	}
	
	public static void main(String[] args) {
		if (args.length > 1) {
			System.out.println("there are more parameters, you just can specify one path parameter.....");
			System.exit(1);
		}
		
		if (args.length == 0) {
			// run the experiment in the local machine for debugging
			args = new String[1];
			args[0] = "../corpus/config.properties";
		}
		
		String[] propArgs = new String[]{"-props", args[0]};
		
		Properties props = StringUtils.argsToProperties(propArgs);
		ExperimentConstructor searchGenerator = new SearchFactory(props);
		searchGenerator.performExperiment();
	}
	
}