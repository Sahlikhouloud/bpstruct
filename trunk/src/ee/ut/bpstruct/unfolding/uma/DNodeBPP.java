/* 
 * Copyright (C) 2010 - Luciano Garcia Banuelos and Artem Polyvyanyy
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ee.ut.bpstruct.unfolding.uma;

import java.util.Iterator;

import hub.top.uma.DNode;
import hub.top.uma.DNodeBP;
import hub.top.uma.DNodeSys;
import hub.top.uma.DNodeBP.Options;

/**
 * This class wraps the UMA one to add the cutoff criteria used during
 * the restructoring of acyclic and cyclic rigid components found in
 * business process models.
 * 
 * @author Artem Polyvyanyy and Luciano Garcia Banuelos
 */
public class DNodeBPP extends DNodeBP {

	public DNodeBPP(DNodeSys system) {
		super(system);
	}
	
	public boolean isCutOffEvent(DNode event) {
		if (findEquivalentCut_conditionHistory_signature_size_Artem(primeConfiguration_Size.get(event), event, currentPrimeCut, bp.getAllEvents())) {
			return true;
		}
		return false;
	}
	
	/**
	   * The search strategy for {@link #equivalentCuts_conditionSignature_history(byte[], DNode[], DNode[])}.
	   * A size-based search strategy ({@link Options#searchStrat_size}). 
	   * 
	   * The method has be extended to determine cut-off events by a lexicographic
	   * search strategy. 
	   * 
	   * @param newEvent  event that has been added to the branching process
	   * @param newCut    the cut reached by 'newEvent'
	   * @param eventsToCompare
	   * @return <code>true</code> iff <code>newEvent</code> is a cut-off event
	   * because of some equivalent event in <code>eventsToCompare</code>.
	   */
	  protected boolean findEquivalentCut_conditionHistory_signature_size_Artem (
	      int newCutConfigSize,
	      DNode newEvent,
	      DNode[] newCut,
	      Iterable<DNode> eventsToCompare)
	  {
	    // all extensions to support the size-lexicographic search-strategy
	    // or marked with LEXIK
	    
	    // optimization: determine equivalence of reached cuts by the
	    // help of a 'condition signature, initialize 'empty' signature
	    // for 'newEvent', see #equivalentCuts_conditionSignature_history
	    byte[] newCutSignature = cutSignature_conditions_init255();
	    
	    // compare the cut reached by 'newEvent' to the initial cut
	    if (newCut.length == bp.initialCut.length)
	      if (equivalentCuts_conditionSignature_history(newCutSignature, newCut, bp.initialCut)) {
	        // yes, newEvent reaches the initial cut again
	        updateCCpair(newEvent, newCut, bp.initialCut);
	        return true; // 'newEvent' is a cut-off event 
	      }
	    
	    // Check whether 'eventsToCompare' contains a "smaller" event that reaches
	    // the same cut as 'newEvent'. 
	    
	    // Optimization: to quickly avoid comparing configurations of events that
	    // do not reach the same cut as 'newEvent', compare and store hash values
	    // of the reached configurations. 
	    
	    // get hash value of the cut reached by 'newEvent'
	    int newEventHash = primeConfiguration_CutHash.get(newEvent);
	    
	    // check whether 'newEvent' is a cut-off event by comparing to all other
	    // given events
	    Iterator<DNode> it = eventsToCompare.iterator();
	    while (it.hasNext()) {
	      DNode e = it.next();
	      
	      // do not check the event that has just been added, the cuts would be equal...
	      if (e == newEvent) continue;
	        
	      // newCut is only equivalent to oldCut if the configuration of newCut
	      // is (lexicographically) larger than the configuration of oldCut
	      if (!primeConfiguration_Size.containsKey(e)) {
	        // the old event 'e' has incomplete information about its prime
	        // configuration, cannot be used to check for cutoff
	        continue;
	      }
	      // optimization: compared hashed values of the sizes of the prime configurations 
	      if (newCutConfigSize < primeConfiguration_Size.get(e)) {
	        // the old one is larger, not equivalent
	        continue;
	      }
	      
	      // optimization: compare reached states by their hash values
	      // only if hash values are equal, 'newEvent' and 'e' could be equivalent 
	      if (primeConfiguration_CutHash.get(e) != newEventHash)
	        continue;

	      // retrieve the cut reached by the old event 'e'
	      DNode[] oldCut = bp.getPrimeCut(e, options.searchStrat_lexicographic, options.searchStrat_lexicographic);
	      
	      // cuts of different lenghts cannot reach the same state, skip
	      if (newCut.length != oldCut.length)
	        continue;

	      // if both configurations have the same size:
	      if (newCutConfigSize == bp.getPrimeConfiguration_size) {
	        
	        // and if not lexicographic, then the new event cannot be cut-off event
	        if (!options.searchStrat_lexicographic) continue;
	        // LEXIK: otherwise compare whether the old event's configuration is
	        // lexicographically smaller than the new event's configuration
	        if (!isSmaller_lexicographic(primeConfigurationString.get(e), primeConfigurationString.get(newEvent)))
	          continue;
	      }
	      
	      boolean doRestrict = true;
//	      if (cyclicNodes.contains(newEvent.getProperName())) { doRestrict = checkCyclicCriterion(newEvent,e,newCut,oldCut); }
//	      else { doRestrict = checkAcyclicCriterion(newEvent,e,newCut,oldCut); }
	      doRestrict = checkAcyclicCriterion(newEvent,e,newCut,oldCut);
	      
	      // The prime configuration of 'e' is either smaller or lexicographically
	      // smaller than the prime configuration of 'newEvent'. Further, both events
	      // reach cuts of the same size. Check whether both cuts reach the same histories
	      // by comparing their condition signatures
	      if (equivalentCuts_conditionSignature_history(newCutSignature, newCut, oldCut) && doRestrict) {
	        // yes, equivalent cuts, make events and conditions equivalent
	        updateCCpair(newEvent, e);
	        updateCCpair(newEvent, newCut, oldCut);
	        // and yes, 'newEvent' is a cut-off event
	        return true;
	      }
	    }
	    // no smaller equivalent has been found
	    return false;
	  }
	  
	  protected String properName(DNode n) {
		  return dNodeAS.properNames[n.id];
	  }
	  /**
	   * Check acyclic structuring criterion, i.e., restrict given cutoff criterion that delivers complete prefix unfolding 
	   * 
	   * @author Artem
	   * 
	   * @param cutoff cutoff event
	   * @param corr corresponding event 
	   * @param cutoff_cut marking of a local configuration of the cutoff event
	   * @param corr_cut marking of a local configuration of the corresponding event
	   * 
	   * @return <code>true</code> if cutoff satisfies acyclic structuring criterion; <code>false</code> otherwise
	   */
	  private boolean checkAcyclicCriterion(DNode cutoff, DNode corr, DNode[] cutoff_cut, DNode[] corr_cut) {
		  if (cutoff.post.length == corr.post.length) {
			  // same postsets
			  int i=0;
			  for (; i<cutoff.post.length; i++) {
				  if (!properName(cutoff.post[i]).equals(properName(corr.post[i]))) {
					  return false;
				  }
			  }
			  
			  // check that cutoff and corr are gateways
//			  if (cutoff.post.length==1 && cutoff.pre.length==1) return false;
//			  if (corr.post.length==1 && corr.pre.length==1) return false;
			  
			  // non-postset conditions are shared () 
			  for (i=0; i<cutoff_cut.length; i++) {
				  DNode curr = cutoff_cut[i]; 
				  boolean doContinue = false;
				  for (int j=0; j < cutoff.post.length; j++) {
					  if (properName(cutoff.post[j]).equals(properName(curr))) {
						  doContinue = true;
						  break;
					  }
				  }
				  if (doContinue) continue;
				  
				  boolean flag = false;
				  for (int k=0; k < corr_cut.length; k++) {
					  if (properName(curr).equals(properName(corr_cut[k])) && curr.globalId==corr_cut[k].globalId) { flag = true; break; }					  
				  }
				  if (!flag) { return false; }
			  }
			  
			  return true;
		  } else { return false; }
	  }
	  
		/**
		 * Option: Print nodes of the branching process that are removed due to
		 * anti-oclets. 
		 */
		public static boolean option_printAnti = true;
		
		/**
		 * Create a GraphViz' dot representation of this branching process.
		 * 
		 * @return 
		 */
		public String toDot () {
			StringBuilder b = new StringBuilder();
			b.append("digraph BP {\n");
			
			// standard style for nodes and edges
			b.append("graph [fontname=\"Helvetica\" nodesep=0.3 ranksep=\"0.2 equally\" fontsize=10];\n");
			b.append("node [fontname=\"Helvetica\" fontsize=8 fixedsize width=\".3\" height=\".3\" label=\"\" style=filled fillcolor=white];\n");
			b.append("edge [fontname=\"Helvetica\" fontsize=8 color=white arrowhead=none weight=\"20.0\"];\n");

			//String tokenFillString = "fillcolor=black peripheries=2 height=\".2\" width=\".2\" ";
			String cutOffFillString = "fillcolor=gold";
			String antiFillString = "fillcolor=red";
			String impliedFillString = "fillcolor=violet";
			String hiddenFillString = "fillcolor=grey";
			
			// first print all conditions
			b.append("\n\n");
			b.append("node [shape=circle];\n");
			for (DNode n : bp.allConditions) {
				
				if (!option_printAnti && n.isAnti)
					continue;
				/* - print current marking
				if (cutNodes.contains(n))
					b.append("  c"+n.localId+" ["+tokenFillString+"]\n");
				else
				*/
				if (n.isAnti && n.isHot)
					b.append("  c"+n.globalId+" ["+antiFillString+"]\n");
	      else if (n.isCutOff)
	        b.append("  c"+n.globalId+" ["+cutOffFillString+"]\n");
				else
					b.append("  c"+n.globalId+" []\n");
				
				String auxLabel = "";
					
				b.append("  c"+n.globalId+"_l [shape=none];\n");
				b.append("  c"+n.globalId+"_l -> c"+n.globalId+" [headlabel=\""+n+" "+auxLabel+"\"]\n");
			}

	    // then print all events
			b.append("\n\n");
			b.append("node [shape=box];\n");
			for (DNode n : bp.allEvents) {
				
				if (!option_printAnti && n.isAnti)
					continue;
				
				
				if (n.isAnti && n.isHot)
					b.append("  e"+n.globalId+" ["+antiFillString+"]\n");
				else if (n.isAnti && !n.isHot)
	        b.append("  e"+n.globalId+" ["+hiddenFillString+"]\n");
				else if (n.isImplied)
	        b.append("  e"+n.globalId+" ["+impliedFillString+"]\n");
				else if (n.isCutOff)
					b.append("  e"+n.globalId+" ["+cutOffFillString+"]\n");
				else
					b.append("  e"+n.globalId+" []\n");
				
	      String auxLabel = "";
	      
				b.append("  e"+n.globalId+"_l [shape=none];\n");
				b.append("  e"+n.globalId+"_l -> e"+n.globalId+" [headlabel=\""+n+" "+auxLabel+"\"]\n");
			}
			
			/*
			b.append("\n\n");
			b.append(" subgraph cluster1\n");
			b.append(" {\n  ");
			for (CNode n : nodes) {
				if (n.isEvent) b.append("e"+n.localId+" e"+n.localId+"_l ");
				else b.append("c"+n.localId+" c"+n.localId+"_l ");
			}
			b.append("\n  label=\"\"\n");
			b.append(" }\n");
			*/
			
			// finally, print all edges
			b.append("\n\n");
			b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=black];\n");
			for (DNode n : bp.allConditions) {
				String prefix = n.isEvent ? "e" : "c";
				for (int i=0; i<n.pre.length; i++) {
					if (n.pre[i] == null) continue;
					if (!option_printAnti && n.isAnti) continue;
					
					if (n.pre[i].isEvent)
						b.append("  e"+n.pre[i].globalId+" -> "+prefix+n.globalId+" [weight=10000.0]\n");
					else
						b.append("  c"+n.pre[i].globalId+" -> "+prefix+n.globalId+" [weight=10000.0]\n");
				}
			}
	    for (DNode n : bp.allEvents) {
	      String prefix = n.isEvent ? "e" : "c";
	      for (int i=0; i<n.pre.length; i++) {
	        if (n.pre[i] == null) continue;
	        if (!option_printAnti && n.isAnti) continue;
	        
	        if (n.pre[i].isEvent)
	          b.append("  e"+n.pre[i].globalId+" -> "+prefix+n.globalId+" [weight=10000.0]\n");
	        else
	          b.append("  c"+n.pre[i].globalId+" -> "+prefix+n.globalId+" [weight=10000.0]\n");
	      }
	    }
	    
	    // add links from cutoffs to corresponding events
	    b.append("\n\n");
		b.append(" edge [fontname=\"Helvetica\" fontsize=8 arrowhead=normal color=red];\n");
		for (DNode n : bp.allEvents) {
			if (n.isCutOff && elementary_ccPair.get(n) != null) {
				b.append("  e"+n.globalId+" -> e"+elementary_ccPair.get(n).globalId+" [weight=10000.0]\n");
			}
		}

			
			b.append("}");
			return b.toString();
		}
}
