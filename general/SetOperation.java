package edu.oregonstate.general;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.stats.Counter;

/**
 * Set Operation
 * 
 * @author Jun Xie (xie@eecs.oregonstate.edu)
 *
 */
public class SetOperation {

	/**
	 * intersection of the keyset of two counter objects
	 * 
	 * @param formerVector
	 * @param latterVector
	 * @return
	 */
	public static Set<String> intersection(Counter<String> formerVector, Counter<String> latterVector) {
		Set<String> commonElementSet = new HashSet<String>();
		
		// get the lower case of the set
//		Set<String> formerSet = StringOperation.lowercase(formerVector.keySet());
//		Set<String> latterSet = StringOperation.lowercase(latterVector.keySet());
		
		Set<String> formerSet = formerVector.keySet();
		Set<String> latterSet = latterVector.keySet();
		
		commonElementSet.addAll(formerSet);
		commonElementSet.retainAll(latterSet);
		
		return commonElementSet;
	}
	
	/**
	 * union of the keyset of two counter objects
	 * 
	 * @param formerVector
	 * @param latterVector
	 * @return
	 */
	public static Set<String> union(Counter<String> formerVector, Counter<String> latterVector) {
		Set<String> union = new HashSet<String>();
		union.addAll(formerVector.keySet());
		union.addAll(latterVector.keySet());
		return union;
	}
}
