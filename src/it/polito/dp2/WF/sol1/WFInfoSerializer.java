package it.polito.dp2.WF.sol1;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import it.polito.dp2.WF.*;

public class WFInfoSerializer {
	private Document doc;
	private WorkflowMonitor monitor;
//	DateFormat dateFormat;

	public WFInfoSerializer() throws WorkflowMonitorException {
		it.polito.dp2.WF.WorkflowMonitorFactory factory = it.polito.dp2.WF.WorkflowMonitorFactory.newInstance();
		monitor = factory.newWorkflowMonitor();
		//dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm ");
	}
	
	private void build() throws ParserConfigurationException{
	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	DocumentBuilder builder = factory.newDocumentBuilder();
	doc = builder.newDocument();
	
	Element rootTag = (Element)doc.createElement("workFlowSystem");
	doc.appendChild(rootTag);
	
	rootTag.appendChild(buildWorkFlows());
	
	}

	private Element buildWorkFlows() {
		Element workFlowsTag = (Element)doc.createElement("workFlows");
		
		Set<WorkflowReader> workFlows = monitor.getWorkflows();
		for (WorkflowReader workFlow : workFlows){
			Element workFlowTag = (Element)doc.createElement("workFlow");
			workFlowsTag.appendChild(workFlowTag);
			
			workFlowTag.setAttribute("flowName", workFlow.getName());
			
			workFlowTag.appendChild(buildActions(workFlow));
			
			workFlowTag.appendChild(buildProcesses(workFlow));

		}
		
		return workFlowsTag;
	}

	private Element buildActions(WorkflowReader workFlow) {
		
		Element actionsTag = (Element)doc.createElement("actions");
		
		Set<ActionReader> actions = workFlow.getActions();
		
		for(ActionReader action: actions) {
			Element actionTag = (Element)doc.createElement("action");
			
			actionsTag.appendChild(actionTag);
			actionTag.setAttribute("actionName",action.getName());
			actionTag.setAttribute("actionRole",action.getRole());
			actionTag.setAttribute("automaticallyInstantiated",(action.isAutomaticallyInstantiated()) ? "true" : "false");
			
			
			if (action instanceof SimpleActionReader) {
				if(buildSimpleAct(action)!=null)
				actionTag.appendChild(buildSimpleAct(action));
		
			}else if (action instanceof ProcessActionReader) {
					Element processActTag = (Element)doc.createElement("processAct");
					actionTag.appendChild(processActTag);
					// print workflow
					processActTag.setAttribute("relatedWorkFlow", ((ProcessActionReader)action).getActionWorkflow().getName());
				}
			

			}
			
			
//			actionTag.appendChild(buildProcessAct(setActions, action));
		
		return actionsTag;
	}


	private Element buildSimpleAct(ActionReader action){

		Set<ActionReader> nextActions = ((SimpleActionReader)action).getPossibleNextActions();
		
		if (nextActions != null){
			
			Element simpleActTag = (Element)doc.createElement("simpleAct");
			for (ActionReader nAct: nextActions){
				
				Element nextActionTag = (Element)doc.createElement("nextAction");
				nextActionTag.setAttribute("nextPossibleAction", nAct.getName());
				simpleActTag.appendChild(nextActionTag);

			}
			return simpleActTag;
		}
		return null;	
	}
	private Element buildProcesses(WorkflowReader workFlow) {
		
		Element processesTag = (Element)doc.createElement("processes");
		Set<ProcessReader> processes = workFlow.getProcesses();
		for(ProcessReader process : processes){
			//System.out.println("SERIALIZER** StartTime: "+formatDate(process.getStartTime()));
			Element processTag = (Element)doc.createElement("process");
			processesTag.appendChild(processTag);
			
			Calendar  startTime = process.getStartTime();
			processTag.setAttribute("startTime",formatDate(startTime) );
			//dateFormat.format(startTime.getTime())
		
			processTag.appendChild(buildActionStatus(process));
		}
		return processesTag;
	}

	private Element buildActionStatus(ProcessReader process) {
		Element actionStatusesTag = (Element)doc.createElement("actionStatuses");
		List<ActionStatusReader> actionStatuses = process.getStatus();
		for(ActionStatusReader actionStatus : actionStatuses){
			
			Element actionStatusTag = (Element)doc.createElement("actionStatus");
			actionStatusesTag.appendChild(actionStatusTag);
			
			actionStatusTag.setAttribute("actionStatName", actionStatus.getActionName());
			
			actionStatusTag.setAttribute("terminated",(actionStatus.isTerminated()) ? "true" : "false");
			
			Calendar terminationTime = actionStatus.getTerminationTime();
			if(terminationTime!= null){
			actionStatusTag.setAttribute("terminationTime", formatDate(terminationTime) );
			//dateFormat.format(terminationTime.getTime()
			}
			//System.out.println("takenInCharge is:" + ((actionStatus.isTakenInCharge()) ? "true" : "false"));

			actionStatusTag.setAttribute("takenInCharge",(actionStatus.isTakenInCharge()) ? "true" : "false");
			
			//System.out.println("terminated is:" + ((actionStatus.isTerminated()) ? "true" : "false"))
			
			Actor actor = actionStatus.getActor();
			
			if (actor!= null){
			Element actorTag = (Element)doc.createElement("actor");
			actionStatusTag.appendChild(actorTag);
			actorTag.setAttribute("actorName",actor.getName());
			actorTag.setAttribute("actorRole",actor.getRole());
			}	
			
		}
		
		return actionStatusesTag;
	}


	private static String formatDate(Calendar calendar) {
		SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss z");
		dateFormat.setTimeZone(calendar.getTimeZone());
		return dateFormat.format(calendar.getTime());
	}

	
	private void serialize(String filename) throws TransformerException, FileNotFoundException {
		TransformerFactory xformFactory = TransformerFactory.newInstance ();
		Transformer idTransform;
		idTransform = xformFactory.newTransformer ();
		idTransform.setOutputProperty(OutputKeys.INDENT, "yes");
		idTransform.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, "wfInfo.dtd");
		Source input = new DOMSource (doc);
		Result output = new StreamResult(new FileOutputStream(filename));
		idTransform.transform (input, output);
	}
	
	public static void main (String[] args) throws WorkflowMonitorException, ParserConfigurationException, TransformerException, FileNotFoundException {
		WFInfoSerializer f = null;
		
		try{
			f = new WFInfoSerializer();
		}catch (WorkflowMonitorException e) {
			System.err.println("Could not instantiate work flow object");
			throw e;
		}
		
		try {
			f.build();
		} catch (ParserConfigurationException e) {
			System.err.println("Could not locate a JAXP DocumentBuilder class");
			throw e;
		}
		
		try {
			f.serialize(args[0]);
		}catch (TransformerException e){
			System.err.println("Serialization error");
			throw e;
		} catch (FileNotFoundException e) {
			System.err.println("File not found exception");
			throw e;
		}
	}
}
