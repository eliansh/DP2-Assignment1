package it.polito.dp2.WF.sol1;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.TimeZone;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import it.polito.dp2.WF.*;

public class Parser {
	private String fileName;
	private HashMap<String, MyWorkFlowReader> workFlows;
	private HashSet<MyProcessReader> allProcess;
	
	public Parser(String fileName, HashMap<String, MyWorkFlowReader> workFlows, HashSet<MyProcessReader> allProcess){
		this.fileName = fileName;
		this.workFlows = workFlows;
		this.allProcess = allProcess;
	}
	
	public void parse() throws WorkflowMonitorException{
		Document document;
		
        DocumentBuilderFactory factory =  DocumentBuilderFactory.newInstance();

        factory.setValidating(true);
        DocumentBuilder builder;
		try {
	        builder = factory.newDocumentBuilder();
		} 
        catch (ParserConfigurationException pce) {
       	 System.err.println("\n** Internal error.");
       	 System.err.println("Parser with specified options can't be built");
        	//pce.printStackTrace();
       	 throw new WorkflowMonitorException(pce);
        }
		
		builder.setErrorHandler(new DomParseVNSHandler());
	    
	    try {
	    	document = builder.parse(new File(fileName));
	    }
        catch (SAXParseException spe) {
            // Parsing error
            System.err.println("\n** Parsing error"
               + ", line " + spe.getLineNumber()
               + ", uri " + spe.getSystemId());
            System.err.println("   " + spe.getMessage() );
            // spe.printStackTrace();
            throw new WorkflowMonitorException(spe);
         } 
         catch (SAXException sxe) {
        	 System.err.println("\n** Internal error.");
        	 System.err.println("   " + sxe.getMessage() );
         	// Exception  x = sxe;
         	// if (sxe.getException() != null)
         	//     x = sxe.getException();
         	// x.printStackTrace();
         	throw new WorkflowMonitorException(sxe);
         } 
         catch (IOException ioe) {
         	// I/O error
        	System.err.println("Unable to read file");
         	// ioe.printStackTrace();
        	throw new WorkflowMonitorException(ioe);
         }
	    
	    DocumentType doctype = document.getDoctype();
	    if (doctype == null) {
		    System.err.println("missing DOCTYPE");
		    throw new WorkflowMonitorException();
	    }
	    
	    if (doctype.getName() != "workFlowSystem")
	    	throw new WorkflowMonitorException();
	    
	    if (doctype.getInternalSubset() != null) {
	    	System.err.println("internal dtd should not be present.");
	    	throw new WorkflowMonitorException();
	    }
	    
	    if (doctype.getSystemId() == null) {
	    	System.err.println("no system dtd reference found");
	    	throw new WorkflowMonitorException();
	    }
		
		Node documentNode = (Node)document;
		if (!documentNode.hasChildNodes())
			throw new WorkflowMonitorException();
		
		Node documentTypeNode = documentNode.getFirstChild();
		
		Node workFlowSystemNode = documentTypeNode.getNextSibling();
		
		Node workFlowsNode = workFlowSystemNode.getFirstChild().getNextSibling();
		
		parseWorkFlowsNode(workFlowsNode);
	}

	private void parseWorkFlowsNode(Node workFlowsNode) throws WorkflowMonitorException {
		Node workFlowsFirstChild = workFlowsNode.getFirstChild();
		if ( workFlowsFirstChild != null){
			Node workFlowNode = workFlowsFirstChild.getNextSibling();
			while (workFlowNode != null) {
				parseWorkFlowNode(workFlowNode);
				workFlowNode = workFlowNode.getNextSibling().getNextSibling();
			}
		}
	}

	private void parseWorkFlowNode(Node workFlowNode) throws WorkflowMonitorException{
		String flowName = workFlowNode.getAttributes().getNamedItem("flowName").getNodeValue();
		if(!MyWorkFlowReader.isNameValid(flowName))
			throw new WorkflowMonitorException();
		
		HashMap<String, MyActionReader> actions = new HashMap<String, MyActionReader>();
		HashSet<MyProcessReader> processes = new HashSet<MyProcessReader>();
		MyWorkFlowReader wfs = new MyWorkFlowReader(actions, flowName, processes);
		
		Node actionsNode = workFlowNode.getFirstChild().getNextSibling();
		Node actionsFirstChild = actionsNode.getFirstChild();
		if(actionsFirstChild != null){
			Node actionNode = actionsFirstChild.getNextSibling();
			while ( actionNode != null){
				NamedNodeMap actionAttributes = actionNode.getAttributes();
				String actionName = actionAttributes.getNamedItem("actionName").getNodeValue();
				if(!MyActionReader.isNameValid(actionName))
					throw new WorkflowMonitorException("action name is not correct");

				String actionRole =actionAttributes.getNamedItem("actionRole").getNodeValue();
				if(!MyActionReader.isRoleValid(actionRole))
					throw new WorkflowMonitorException("action role is not correct");
				boolean automaticallyInstantiated = (actionAttributes.getNamedItem("automaticallyInstantiated").getNodeValue().equals("true")) ? true : false;
				
				MyActionReader action = new MyActionReader(wfs, actionName, actionRole, automaticallyInstantiated);
				Node actionChildNode = actionNode.getFirstChild();
				if(actionChildNode!=null){
					Node actionChild = actionChildNode.getNextSibling();
						if(actionChild.getNodeName().contains("simpleAct")){
							Node simpleActFirstChild = actionChild.getFirstChild();
							HashSet<MyActionReader> nextActions = new HashSet<MyActionReader>();
							if(simpleActFirstChild!=null){
								Node nextActionNode = simpleActFirstChild.getNextSibling();			
								while (nextActionNode != null){
									NamedNodeMap nextActionAttributes = nextActionNode.getAttributes();
									String nextPossibleAction = nextActionAttributes.getNamedItem("nextPossibleAction").getNodeValue();
									nextActions.add(actions.get(nextPossibleAction));
									nextActionNode = nextActionNode.getNextSibling().getNextSibling();
									}
							}
									action = new MySimpleActionReader(wfs, actionName, actionRole, automaticallyInstantiated, nextActions);
						
						}else if(actionChild.getNodeName().contains("processAct")) {
							Node processActNode = actionChild;
							if ( processActNode != null){
								NamedNodeMap processActAttribute = processActNode.getAttributes();
								String relatedWorkFlow = processActAttribute.getNamedItem("relatedWorkFlow").getNodeValue();
								MyWorkFlowReader work = null;
								for(MyWorkFlowReader wf:workFlows.values()){
									if(relatedWorkFlow.contains(wf.getName())){
										work = wf;
									}
								}
								action = new MyProcessActionReader(work,wfs, actionName, actionRole, automaticallyInstantiated);
							}
						}
						
				}	
				actions.put(actionName, action);
				actionNode = actionNode.getNextSibling().getNextSibling();	
			}
		}
		Node processesNode = actionsNode.getNextSibling().getNextSibling();
		Node processesFirstChild = processesNode.getFirstChild();
		
		if(processesFirstChild != null){
			Node processNode = processesFirstChild.getNextSibling();
			while ( processNode != null){
				NamedNodeMap processAttributes = processNode.getAttributes();
				Calendar startTime = null;
				try {
					startTime = parseDate(processAttributes.getNamedItem("startTime").getNodeValue());
				} catch (DOMException | ParseException e) {
					throw new WorkflowMonitorException(e);
				}	
				ArrayList<MyActionStatusReader> actionStatuses= new ArrayList<MyActionStatusReader>();
				Node actionStatusesNode = processNode.getFirstChild().getNextSibling();

				Node actionStatusesFirstChild = actionStatusesNode.getFirstChild();
				if(actionStatusesFirstChild!=null){
					Node actionStatusNode = actionStatusesFirstChild.getNextSibling();
				
					while(actionStatusNode!=null){

						NamedNodeMap statusAttributes = actionStatusNode.getAttributes();
						String actionStatName = statusAttributes.getNamedItem("actionStatName").getNodeValue();	
						if(!MyActionStatusReader.isNameValid(actionStatName))
							throw new WorkflowMonitorException("action name is not correct"+actionStatName);
						boolean terminated = (statusAttributes.getNamedItem("terminated").getNodeValue().equals("true")) ? true : false;
						Calendar terminationTime = null;
						if(terminated){
						try {
							terminationTime = parseDate(statusAttributes.getNamedItem("terminationTime").getNodeValue());

						} catch (DOMException | ParseException e) {
							throw new WorkflowMonitorException(e);
						}
						}	
	
						boolean takenInCharge = (statusAttributes.getNamedItem("takenInCharge").getNodeValue().equals("true")) ? true : false;
						Actor actor = null;
						if(takenInCharge){
						Node actorNode = actionStatusNode.getFirstChild().getNextSibling();
						if(actorNode != null){
						NamedNodeMap actorAttributes = actorNode.getAttributes();
						String actorName = actorAttributes.getNamedItem("actorName").getNodeValue();
						String actorRole = actorAttributes.getNamedItem("actorRole").getNodeValue();
						actor = new Actor(actorName, actorRole);
						}
						}
						
						MyActionStatusReader actionStatus = new MyActionStatusReader(actionStatName, actor, terminationTime, takenInCharge, terminated);

						actionStatuses.add(actionStatus);
						actionStatusNode = actionStatusNode.getNextSibling().getNextSibling();	
					}
				}
				MyProcessReader process = new MyProcessReader(startTime,actionStatuses, wfs);
				processes.add(process);
				processNode = processNode.getNextSibling().getNextSibling();
			}
			
		}
		allProcess.addAll(processes);
		MyWorkFlowReader wf = new MyWorkFlowReader(actions, flowName, processes);
		workFlows.put(flowName, wf);
	}
			
			private static Calendar parseDate(String string) throws ParseException {
				DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z");
				Calendar cal  = Calendar.getInstance();
				dateFormat.setTimeZone(TimeZone.getTimeZone("CEST"));
				cal.setTime(dateFormat.parse(string));
				return cal;
			}
			
			private static String formatDate(Calendar calendar) {
				SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z");
				dateFormat.setTimeZone(calendar.getTimeZone());
				return dateFormat.format(calendar.getTime());
			}
}

class DomParseVNSHandler extends org.xml.sax.helpers.DefaultHandler {

	  // Validation errors are treated as fatal
	  public void error (SAXParseException e) throws SAXParseException
	  {
	    throw e;
	  }

	  // Warnings are displayed (without terminating)
	  public void warning (SAXParseException e) throws SAXParseException
	  {
	    System.err.println("** Warning"
	            + ", file " + e.getSystemId()
	            + ", line " + e.getLineNumber());
	    System.err.println("   " + e.getMessage() );
	  }
}
