/*
 *  Copyright (c) 1995-2018, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Yaoyong Li 15/03/2009
 *
 *  $Id: IaaMain.java, v 1.0 2009-03-15 12:58:16 +0000 yaoyong $
 */

package gate.bdmComp;

import gate.Factory;
import gate.ProcessingResource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.creole.ontology.OClass;
import gate.creole.ontology.OConstants;
import gate.creole.ontology.Ontology;
import gate.util.Files;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Deprecated - use the {@link BDMTool} resource helper instead.
 */
@Deprecated
@CreoleResource(name = "BDM Computation PR (deprecated)",
        comment = "Compute BDM scores - this action can be accessed more simply " +
                "via the right-click menu on an Ontology LR",
        helpURL = "http://gate.ac.uk/userguide/sec:eval:bdmplugin")
public class BDMCompMain extends AbstractLanguageAnalyser implements
ProcessingResource {
  /** The ontology used. */
  Ontology ontology = null;
  /** The file storing the BDM score. */
  URL outputBDMFile = null;
  /** store the BDM information for each pair of concepts. */
  Set<BDMOne>bdmScores = null;
  
	/** Initialise this resource, and return it. */
	public gate.Resource init() throws ResourceInstantiationException {
    bdmScores = new HashSet<BDMOne>();
		return this;
	} // init()
	
	HashMap<OClass,Integer> concept2id= new HashMap<OClass,Integer>();
	/**
	 * Run the resource.
	 * 
	 * @throws ExecutionException
	 */
	public void execute() throws ExecutionException {
	  //open the result file
	  if(corpus != null) {
	    if(corpus.size() != 0)
	      if(corpus.indexOf(document)>0)
	        return;
	  }
	  BufferedWriter bdmResultsWriter = null;
	  boolean isExistingResultFile = false;
	  try {
	    if(outputBDMFile != null && !outputBDMFile.toString().equals("")) {
	      bdmResultsWriter = new BufferedWriter(new OutputStreamWriter(
	        new FileOutputStream(Files.fileFromURL(outputBDMFile)), "UTF-8"));
	      isExistingResultFile = true;
	    }
	    else {
	      System.out.println("There is no file specified for storing the BDM scores!");
	    }
    
	  /** load the ontology. */ 
	  //if(ontologyUsed == null || ontologyUsed.toString().trim() == "") {
	    if(ontology == null) {
        throw new ExecutionException("No ontology: neither using a loaded ontology nor giving the ontology URL!");
	    }
	    /*else { 
	      // step 3: set the parameters 
	      
	    }*/
	  //}

      // do the work
      bdmScores = BDMTool.computeBDMStatic(ontology, isExistingResultFile ? bdmResultsWriter : System.out);

      if(isExistingResultFile) {
        bdmResultsWriter.flush();
      }

	  }
    catch(IOException e) {
      e.printStackTrace();
    }
    finally { // in any case
      if(isExistingResultFile) {
        try {
          // close the output file
          bdmResultsWriter.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
	}

  @RunTime
  @CreoleParameter(comment = "The ontology used for BDM computing")
	public void setOntology(Ontology ontology) {
    this.ontology = ontology;
  }

  public Ontology getOntology() {
    return this.ontology;
  }

  @Optional
  @RunTime
  @CreoleParameter(comment = "The file to which scores should be written - if " +
          "omitted the scores are printed to standard output")
  public void setOutputBDMFile(URL ontoU) {
    this.outputBDMFile = ontoU;
  }

  public URL getOutputBDMFile() {
    return this.outputBDMFile;
  }
  

}
