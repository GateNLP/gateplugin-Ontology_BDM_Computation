/*
 *  Copyright (c) 1995-2018, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Lesser General Public License,
 *  Version 3, June 2007 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Ian Roberts, based on code by Yaoyong Li
 *
 */
package gate.bdmComp;

import gate.creole.ExecutionException;
import gate.creole.metadata.AutoInstance;
import gate.creole.metadata.CreoleResource;
import gate.creole.ontology.OClass;
import gate.creole.ontology.OConstants;
import gate.creole.ontology.Ontology;
import gate.gui.MainFrame;
import gate.gui.NameBearerHandle;
import gate.gui.ResourceHelper;
import gate.swing.XJFileChooser;
import gate.util.ExtensionFileFilter;
import org.apache.commons.io.output.NullWriter;
import org.apache.log4j.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@CreoleResource(name = "Calculate BDM scores",
        comment = "Calculate BDM scores for all pairs of concepts in this ontology",
        isPrivate = true, tool = true, autoinstances = @AutoInstance)
public class BDMTool extends ResourceHelper {
  private static final Logger log = Logger.getLogger(BDMTool.class);

  @Override
  protected List<Action> buildActions(NameBearerHandle handle) {
    if(!(handle.getTarget() instanceof Ontology)) {
      return Collections.emptyList();
    } else {
      return Collections.singletonList(new BDMAction((Ontology) handle.getTarget()));
    }
  }

  public static class BDMAction extends AbstractAction {
    private Ontology ontology;

    public BDMAction(Ontology ontology) {
      super("Calculate BDM scores");
      this.ontology = ontology;
    }

    public void actionPerformed(ActionEvent e) {
      XJFileChooser fileChooser = MainFrame.getFileChooser();

      ExtensionFileFilter filter = new ExtensionFileFilter(
              "Text files (.txt)", ".txt");
      fileChooser.resetChoosableFileFilters();
      fileChooser.addChoosableFileFilter(filter);
      fileChooser.setFileFilter(filter);
      fileChooser.setDialogTitle("Save calculated BDM scores");
      fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      fileChooser.setResource(BDMCompMain.class.getName());

      if(fileChooser.showSaveDialog(
              MainFrame.getInstance()) != XJFileChooser.APPROVE_OPTION)
        return;

      File outputFile = fileChooser.getSelectedFile();

      if(outputFile == null) return;

      MainFrame.lockGUI("Calculating BDM scores");
      new Thread(() -> {
        try(PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8))) {
          computeBDMStatic(ontology, pw);
        } catch(IOException | ExecutionException ex) {
          ex.printStackTrace();
        } finally {
          MainFrame.unlockGUI();
        }
      }).start();
    }
  }

  public Set<BDMOne> computeBDM(Ontology ontology, Appendable output) throws IOException, ExecutionException {
    return computeBDMStatic(ontology, output);
  }

  /**
   * The actual implementation of the BDM calculation.
   *
   * @param ontology the ontology to compute scores for
   * @param output where to send the output, or <code>null</code> if you only want the return value.
   * @return the set of calculated scores
   */
  public static Set<BDMOne> computeBDMStatic(Ontology ontology, Appendable output) throws IOException, ExecutionException {
    if(ontology == null) {
      throw new ExecutionException("No ontology provided for BDM computation!");
    }

    if(output == null) {
      // a do-nothing Appendable so we don't have to worry about nulls later on
      output = NullWriter.NULL_WRITER;
    }

    output.append("##The following are the BDM scores for ");
    output.append("each pair of concepts in the ontology named ");
    output.append(ontology.getName()).append(".\n");

    HashMap<OClass,Integer> concept2id= new HashMap<OClass,Integer>();
    /* store the BDM information for each pair of concepts. */
    Set<BDMOne>bdmScores = new HashSet<>();

    // retrieving a list of top classes
    Set<OClass> topClasses = ontology.getOClasses(true);
    if(topClasses.size()>1) {
      log.debug("The ontology has "+topClasses.size() +" top classes!!");
    }
    // retrieving a list of all classes
    Set<OClass> allConcepts = ontology.getOClasses(false);
    //assign a number id to each class

    HashMap<Integer,OClass> id2concept= new HashMap<Integer,OClass>();
    int num=1;
    for(OClass ob:allConcepts) {
      concept2id.put(ob, Integer.valueOf(num));
      id2concept.put(Integer.valueOf(num), ob);
      //System.out.println(num+", *"+ob.getName()+"*"+", id="+concept2id.get(ob).intValue()+"*");
      ++num;
    }

    log.debug("ontology "+ontology.getName()+", allConcepts="+allConcepts.size());

    //for each concept, get the chain from it to the top class
    HashMap<OClass,String> concept2chain = new HashMap<OClass,String>();
    //obtainChains(concept2id, concept2chain);
    num=1;
    for(OClass curCon:allConcepts) {
      String chainSofar = "";
      int numS = curCon.getSuperClasses(OConstants.Closure.DIRECT_CLOSURE).size();
      //if(numS>1)
      //System.out.println("****** curCon="+curCon.getName()+"*"+", num="+numS+"*");
      String chains = obtainAChain(ontology, concept2id, curCon, chainSofar);
      concept2chain.put(curCon, chains);
      //chainId.append(concept2id.get(curCon).toString());
        /*String [] idsC = chains.split(ConstantParameters.separater2);
        for(int i=0; i<idsC.length; ++i) {
          String [] oneC = idsC[i].split(ConstantParameters.separater1);
          String conChains="";
          for(int j=0; j<oneC.length; ++j)
            conChains += " "+ id2concept.get(new Integer(oneC[j])).getName();
          System.out.println("num="+num+", concept:"+curCon.getName()+", chain="+conChains);
        }*/
      ++num;
    }
    //get the leaf nodes, and the chain length for each leafy node
    HashMap<OClass,String> leafyCon2Chain = new HashMap<OClass,String>();
    num = 1;
    for(OClass curCon:allConcepts) {
      if(curCon.getSubClasses(OConstants.Closure.DIRECT_CLOSURE).size()==0) {
        //System.out.println(num+", leafy node="+curCon.getName()+"*");
        leafyCon2Chain.put(curCon, concept2chain.get(curCon));
        ++num;
      }
    }
    //compute the chain length coming through one node
    HashMap<OClass, Float> con2ChainLen = new HashMap<OClass, Float>();
    HashMap<OClass, Integer> con2ChainNum = new HashMap<OClass, Integer>();
    float n0BDM=0.0f;
    num = 0;
    for(OClass curCon:leafyCon2Chain.keySet()) {
      String [] idsC = leafyCon2Chain.get(curCon).split(ConstantParameters.separater2);
      String lenS = "";
      for(int i=0; i<idsC.length; ++i) {
        String [] oneC = idsC[i].split(ConstantParameters.separater1);
        int len = oneC.length-1;
        n0BDM += len;
        lenS += len + " ";
        //System.out.println(num+", con="+curCon.getName()+", len="+len+", lenS="+lenS+"*");
        ++num;
        //get each concept from the chain
        for(int j=0; j<oneC.length; ++j) {
          OClass con = id2concept.get(new Integer(oneC[j]));
          if(con2ChainLen.containsKey(con)) {
            int len00= con2ChainLen.get(con).intValue()+len;
            con2ChainLen.put(con, Float.valueOf(len00));
          } else {
            con2ChainLen.put(con, Float.valueOf(len));
          }
          if(con2ChainNum.containsKey(con)) {
            int len00= con2ChainNum.get(con).intValue()+1;
            con2ChainNum.put(con, Integer.valueOf(len00));
          } else {
            con2ChainNum.put(con, Integer.valueOf(1));
          }
        }
      }
      lenS = lenS.trim();
      //leafyCon2ChainLen.put(curCon, lenS);
    }
    if(num>1) n0BDM /= num;
    //compute the average chain length for each concept
    num=1;
    for(OClass curCon:con2ChainLen.keySet()) {
      float len = con2ChainLen.get(curCon).floatValue();
      len /= con2ChainNum.get(curCon).intValue();
      con2ChainLen.put(curCon, Float.valueOf(len));
      //curCon = (OClass) ontologyUsed.getOResourceByName(curCon.getName());
      //System.out.println(num+", con="+curCon.getName()+", averlen="+len+"*"+", num="+con2ChainNum.get(curCon).intValue());
      ++num;
    }
    //compute the number of branches for each concept
    HashMap<OClass,Integer>concept2branch = new HashMap<OClass,Integer>();
    float averBran = 0.0f;
    num = 0;
    for(OClass curCon:allConcepts) {
      int len = curCon.getSubClasses(OClass.DIRECT_CLOSURE).size();
      if(len>0) {
        concept2branch.put(curCon, Integer.valueOf(len));
        averBran += len;
        ++num;
      }
    }
    if(num>1) averBran /= num;
    //Now compute the BDM for each pair of concept

    for(OClass curCon11:allConcepts) {
      String [] chainS11 = concept2chain.get(curCon11).split(ConstantParameters.separater2);
      for(OClass curCon22:allConcepts) {
        int id11 = concept2id.get(curCon11).intValue();
        int id22 = concept2id.get(curCon22).intValue();
        if(id11<id22) continue;
        BDMOne bdmS = new BDMOne(curCon11, curCon22);
        if(id11==id22) {
          //get the shortest chain
          int len=Integer.MAX_VALUE;
          for(int i=0; i<chainS11.length; ++i) {
            String [] items = chainS11[i].split(ConstantParameters.separater1);
            if(len>items.length) len = items.length;
          }
          len -=1;
          bdmS.setValues(1.0f, len, 0, 0, n0BDM, 1, 1, 1);
          bdmS.setMsca(curCon11);
          bdmScores.add(bdmS);

          continue;
        }
        String [] chainS22 = concept2chain.get(curCon22).split(ConstantParameters.separater2);
        int lenS11 = chainS11.length;
        int lenS22 = chainS22.length;
        for(int iS11=0; iS11<lenS11; ++iS11) {
          for(int iS22=0; iS22<lenS22; ++iS22) {
            //determine the common part of the two chains
            String [] chain11 = chainS11[iS11].split(ConstantParameters.separater1);
            String [] chain22 = chainS22[iS22].split(ConstantParameters.separater1);
            int len11 = chain11.length;
            int len22 = chain22.length;
            int len00=len11;
            if(len00>len22) len00 = len22;
            int cp =0;
            for(int ii=0; ii<len00; ++ii) {
              if(chain11[len11-1-ii].equals(chain22[len22-1-ii])) {
                ++cp;
              }
              else break;
            }
            //System.out.println("cp="+cp+", ("+curCon11.getName()+","+curCon22.getName()+
            //"), ch1="+chainS11[iS11]+",ch2="+chainS22[iS22]);
            float m1, m2;
            m1 = con2ChainLen.get(curCon11).floatValue();
            m2 = con2ChainLen.get(curCon22).floatValue();
            if(cp==0) { //the two concepts are not in the same connect part of ontology
              bdmS.setValues(0, -1, len11-1, len22-1, n0BDM, m1, m2, 1.0f);
            } else {
              Integer commonConId;
              commonConId=new Integer(chain11[len11-cp]); //get the common concept
              OClass commonCon = id2concept.get(commonConId);

              //System.out.println("comId="+commonConId.intValue()+", con="+commonCon.getName());

              cp -= 1; //count the edges, not the nodes
              int dpk, dpr;
              dpk = len11-1-cp;
              dpr = len22-1-cp;
              //compute the averaged branch in the chain from key to response
              int num11=0;
              float bran = 0.0f;
              if(concept2branch.containsKey(commonCon))
                bran += concept2branch.get(commonCon).intValue();
              ++num11;

              for(int i=1; i<len11-cp-1; ++i) {
                OClass con = id2concept.get(new Integer(chain11[i]));
                if(concept2branch.containsKey(con))
                  bran += concept2branch.get(con).intValue();
                ++num11;
              }
              for(int i=1; i<len22-cp-1; ++i) {
                OClass con = id2concept.get(new Integer(chain22[i]));
                if(concept2branch.containsKey(con))
                  bran += concept2branch.get(con).intValue();
                ++num11;
              }
              if(num11>1) bran /= num11;
              bran /= averBran;
              //compute the bdm score for the two chains
              float bdm = bran*cp/n0BDM;
              bdm = bdm/(bdm+dpk/m1 + dpr/m2);
              if(bdm>bdmS.bdmScore) {
                bdmS.setValues(bdm, cp, dpk, dpr, n0BDM, m1, m2, bran);
              }
              bdmS.setMsca(commonCon);
            }
          }
        } //end of the loop for the chains of the two concepts
        bdmScores.add(bdmS);

      }//end of the loop for the second concept
    }//end of the loop for the first concept
    //write the results into a file or console
    for(BDMOne oneb:bdmScores) {
      String text = oneb.printResult();
      output.append(text).append("\n");
    }

    return bdmScores;
  }

  /** recursive function to get the chain */
  static String obtainAChain(Ontology ontology, Map<OClass, Integer> concept2id, OClass curCon, String chainSofar) {
    String chainNow = "";
    //String chainSofar = "";
    //System.out.println("conCur=*"+curCon.getName()+"*"+", chainsofar=*"+chainSofar+"*");
    //System.out.println("conId="+concept2id.get(curCon)+"*");
    chainSofar += concept2id.get(curCon).toString();
    if(curCon.isTopClass()) {
      chainNow = chainSofar + ConstantParameters.separater2;
      return chainNow;
    } else {
      Set<OClass> superCons =
              curCon.getSuperClasses(OConstants.Closure.DIRECT_CLOSURE);
      chainSofar += ConstantParameters.separater1;
      //if(superCons.size()>1) {
      // System.out.println("****** curCon="+curCon.getName()+"*"+", num="+superCons.size());
      //}
      for(OClass oneCon:superCons) {
        oneCon = (OClass) ontology.getOResourceByName(oneCon.getName());
        chainNow +=  obtainAChain(ontology, concept2id, oneCon, chainSofar);
      }
    }
    return chainNow;
  }
}
