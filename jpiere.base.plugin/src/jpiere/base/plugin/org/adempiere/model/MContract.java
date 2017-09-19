/******************************************************************************
 * Product: JPiere                                                            *
 * Copyright (C) Hideaki Hagiwara (h.hagiwara@oss-erp.co.jp)                  *
 *                                                                            *
 * This program is free software, you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY.                          *
 * See the GNU General Public License for more details.                       *
 *                                                                            *
 * JPiere is maintained by OSS ERP Solutions Co., Ltd.                        *
 * (http://www.oss-erp.co.jp)                                                 *
 *****************************************************************************/

package jpiere.base.plugin.org.adempiere.model;

import java.io.File;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;

import org.compiere.model.MDocType;
import org.compiere.model.MFactAcct;
import org.compiere.model.MPeriod;
import org.compiere.model.MQuery;
import org.compiere.model.ModelValidationEngine;
import org.compiere.model.ModelValidator;
import org.compiere.model.PrintInfo;
import org.compiere.model.Query;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportEngine;
import org.compiere.process.DocAction;
import org.compiere.process.DocOptions;
import org.compiere.process.DocumentEngine;
import org.compiere.process.ProcessInfo;
import org.compiere.process.ServerProcessCtl;
import org.compiere.util.CCache;
import org.compiere.util.DB;
import org.compiere.util.Msg;
import org.compiere.util.Util;

/** JPIERE-0363
*
* @author Hideaki Hagiwara
*
*/
public class MContract extends X_JP_Contract implements DocAction,DocOptions
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -7588955558162632796L;


	public MContract(Properties ctx, int JP_Contract_ID, String trxName) 
	{
		super(ctx, JP_Contract_ID, trxName);
	}
	
	public MContract(Properties ctx, ResultSet rs, String trxName) 
	{
		super(ctx, rs, trxName);
	}

	/**
	 * 	Get Document Info
	 *	@return document info (untranslated)
	 */
	public String getDocumentInfo()
	{
		MDocType dt = MDocType.get(getCtx(), 0);
		return dt.getNameTrl() + " " + getDocumentNo();
	}	//	getDocumentInfo

	/**
	 * 	Create PDF
	 *	@return File or null
	 */
	public File createPDF ()
	{
		try
		{
			File temp = File.createTempFile(get_TableName()+get_ID()+"_", ".pdf");
			return createPDF (temp);
		}
		catch (Exception e)
		{
			log.severe("Could not create PDF - " + e.getMessage());
		}
		return null;
	}	//	getPDF

	/**
	 * 	Create PDF file
	 *	@param file output file
	 *	@return file if success
	 */
	public File createPDF (File file)
	{
		// set query to search this document
		int m_docid = getJP_Contract_ID();
		MQuery query = new MQuery(Table_Name);
		query.addRestriction( COLUMNNAME_JP_Contract_ID, MQuery.EQUAL, new Integer(m_docid));
	
		// get Print Format
		//int AD_PrintFormat_ID = 1000133;
		//System.out.print(getC_DocTypeTarget_ID());
		int AD_PrintFormat_ID = getC_DocType().getAD_PrintFormat_ID();
		MPrintFormat pf = new  MPrintFormat(getCtx(), AD_PrintFormat_ID, get_TrxName());
	
		// set PrintInfo (temp)
		PrintInfo info = new PrintInfo("0", 0, 0, 0);
	
		// Create ReportEngine
		//ReportEngine re = ReportEngine.get(getCtx(), ReportEngine.JPE,  getJP_Estimation_ID(), get_TrxName());
		ReportEngine re = new ReportEngine(getCtx(), pf, query, info);
	
		// For JaperReport
		//System.out.print("PrintFormat: " + re.getPrintFormat().get_ID());
		//MPrintFormat format = re.getPrintFormat();
		// We have a Jasper Print Format
		// ==============================
		if(pf.getJasperProcess_ID() > 0)
		{
			ProcessInfo pi = new ProcessInfo ("", pf.getJasperProcess_ID());
			pi.setRecord_ID ( getJP_Contract_ID() );
			pi.setIsBatch(true);

			ServerProcessCtl.process(pi, null);

			return pi.getPDFReport();

		}
		// Standard Print Format (Non-Jasper)
		// ==================================

		return re.getPDF(file);
	}	//	createPDF

	
	/**************************************************************************
	 * 	Process document
	 *	@param processAction document action
	 *	@return true if performed
	 */
	public boolean processIt (String processAction)
	{
		m_processMsg = null;
		DocumentEngine engine = new DocumentEngine (this, getDocStatus());
		return engine.processIt (processAction, getDocAction());
	}	//	processIt
	
	/**	Process Message 			*/
	private String		m_processMsg = null;
	/**	Just Prepared Flag			*/
	private boolean		m_justPrepared = false;

	/**
	 * 	Unlock Document.
	 * 	@return true if success 
	 */
	public boolean unlockIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("unlockIt - " + toString());
		setProcessing(false);
		return true;
	}	//	unlockIt
	
	/**
	 * 	Invalidate Document
	 * 	@return true if success 
	 */
	public boolean invalidateIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("invalidateIt - " + toString());
		setDocAction(DOCACTION_Prepare);
		return true;
	}	//	invalidateIt
	
	/**
	 *	Prepare Document
	 * 	@return new status (In Progress or Invalid) 
	 */
	public String prepareIt()
	{
		if (log.isLoggable(Level.INFO)) log.info(toString());
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());

		//	Std Period open?
		if (!MPeriod.isOpen(getCtx(), getDateAcct(), dt.getDocBaseType(), getAD_Org_ID()))
		{
			m_processMsg = "@PeriodClosed@";
			return DocAction.STATUS_Invalid;
		}
		
		//Check Lines
		if(getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_PeriodContract))
		{
			MContractContent[] contents = getContractContents();
			if(contents.length == 0)
			{
				m_processMsg = "@NoLines@";
				return DocAction.STATUS_Invalid;
			}
			
			for(int i = 0; i < contents.length; i++)
			{
				MContractLine[] lines = contents[i].getLines();
				if (lines.length == 0)
				{
					m_processMsg = "@NoLines@";
					return DocAction.STATUS_Invalid;
				}
			}
			
		}else if(getJP_ContractType().equals(MContract.JP_CONTRACTTYPE_SpotContract)){
			
			MContractContent[] contents = getContractContents();
			if(contents.length == 0)
			{
				m_processMsg = "@NoLines@";
				return DocAction.STATUS_Invalid;
			}
			
		}
		

		
		
		//	Add up Amounts
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_PREPARE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		m_justPrepared = true;
		if (!DOCACTION_Complete.equals(getDocAction()))
			setDocAction(DOCACTION_Complete);
		return DocAction.STATUS_InProgress;
	}	//	prepareIt
	
	/**
	 * 	Approve Document
	 * 	@return true if success 
	 */
	public boolean  approveIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("approveIt - " + toString());
		setIsApproved(true);
		return true;
	}	//	approveIt
	
	/**
	 * 	Reject Approval
	 * 	@return true if success 
	 */
	public boolean rejectIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("rejectIt - " + toString());
		setIsApproved(false);
		return true;
	}	//	rejectIt
	
	/**
	 * 	Complete Document
	 * 	@return new status (Complete, In Progress, Invalid, Waiting ..)
	 */
	public String completeIt()
	{
		//	Re-Check
		if (!m_justPrepared)
		{
			String status = prepareIt();
			m_justPrepared = false;
			if (!DocAction.STATUS_InProgress.equals(status))
				return status;
		}

		 setDefiniteDocumentNo();

		m_processMsg = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_BEFORE_COMPLETE);
		if (m_processMsg != null)
			return DocAction.STATUS_Invalid;
		
		//	Implicit Approval
	//	if (!isApproved())
			approveIt();
		if (log.isLoggable(Level.INFO)) log.info(toString());
		//
		
		//	User Validation
		String valid = ModelValidationEngine.get().fireDocValidate(this, ModelValidator.TIMING_AFTER_COMPLETE);
		if (valid != null)
		{
			m_processMsg = valid;
			return DocAction.STATUS_Invalid;
		}
		
		setProcessed(true);
		setDocAction(DOCACTION_Close);
		updateContractStatus(DOCACTION_Complete);
		
		return DocAction.STATUS_Completed;
	}	//	completeIt
	
	/**
	 * 	Set the definite document number after completed
	 */
	private void setDefiniteDocumentNo() {
		MDocType dt = MDocType.get(getCtx(), getC_DocType_ID());
		if (dt.isOverwriteDateOnComplete()) {
			setDateAcct(new Timestamp (System.currentTimeMillis()));
			MPeriod.testPeriodOpen(getCtx(), getDateAcct(), getC_DocType_ID(), getAD_Org_ID());
			
		}
		if (dt.isOverwriteSeqOnComplete()) {
			String value = null;
			int index = p_info.getColumnIndex("C_DocType_ID");
			if (index != -1)		//	get based on Doc Type (might return null)
				value = DB.getDocumentNo(get_ValueAsInt(index), get_TrxName(), true);
			if (value != null) {
				setDocumentNo(value);
			}
		}
	}
	

	/**
	 * 	Void Document.
	 * 	Same as Close.
	 * 	@return true if success 
	 */
	public boolean voidIt()
	{
		if (log.isLoggable(Level.INFO)) log.info(toString());
		// Before Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_VOID);
		if (m_processMsg != null)
			return false;

		MFactAcct.deleteEx(MEstimation.Table_ID, getJP_Contract_ID(), get_TrxName());
		setPosted(true);
		
		MContractContent[] contents = getContractContents();
		for(int i = 0; i <contents.length; i++)
		{
			boolean isOK = contents[i].processIt(DocAction.ACTION_Void);
			if(isOK)
				contents[i].saveEx(get_TrxName());
		}

		// After Void
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_VOID);
		if (m_processMsg != null)
			return false;

		setProcessed(true);
		
		updateContractStatus(DOCACTION_Void);
		
		setDocAction(DOCACTION_None);

		return true;
	}	//	voidIt
	
	/**
	 * 	Close Document.
	 * 	Cancel not delivered Qunatities
	 * 	@return true if success 
	 */
	public boolean closeIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("closeIt - " + toString());

		
		MContractContent[] contents = getContractContents();
		for(int i = 0; i <contents.length; i++)
		{
			boolean isOK = contents[i].processIt(DocAction.ACTION_Close);
			if(isOK)
				contents[i].saveEx(get_TrxName());
		}
		
		setProcessed(true);//Special specification For Contract Document to update Field in case of DocStatus == 'CO'
		setDocAction(DOCACTION_None);
		updateContractStatus(DOCACTION_Close);
		
		return true;
	}	//	closeIt
	
	/**
	 * 	Reverse Correction
	 * 	@return true if success 
	 */
	public boolean reverseCorrectIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("reverseCorrectIt - " + toString());
		return false;
	}	//	reverseCorrectionIt
	
	/**
	 * 	Reverse Accrual - none
	 * 	@return true if success 
	 */
	public boolean reverseAccrualIt()
	{
		if (log.isLoggable(Level.INFO)) log.info("reverseAccrualIt - " + toString());
		return false;
	}	//	reverseAccrualIt
	
	/** 
	 * 	Re-activate
	 * 	@return true if success 
	 */
	public boolean reActivateIt()
	{
		if (log.isLoggable(Level.INFO)) log.info(toString());
		// Before reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_BEFORE_REACTIVATE);
		if (m_processMsg != null)
			return false;

		MFactAcct.deleteEx(MEstimation.Table_ID, getJP_Contract_ID(), get_TrxName());
		setPosted(false);

		// After reActivate
		m_processMsg = ModelValidationEngine.get().fireDocValidate(this,ModelValidator.TIMING_AFTER_REACTIVATE);
		if (m_processMsg != null)
			return false;

		setDocAction(DOCACTION_Complete);
		setProcessed(false);

		return true;
	}	//	reActivateIt
	
	
	/*************************************************************************
	 * 	Get Summary
	 *	@return Summary of Document
	 */
	public String getSummary()
	{
		return getDocumentNo();
	}	//	getSummary


	/**
	 * 	Get Process Message
	 *	@return clear text error message
	 */
	public String getProcessMsg()
	{
		return m_processMsg;
	}	//	getProcessMsg
	
	/**
	 * 	Get Document Owner (Responsible)
	 *	@return AD_User_ID
	 */
	public int getDoc_User_ID()
	{
		return getSalesRep_ID();
	}	//	getDoc_User_ID

	/**
	 * 	Get Document Approval Amount
	 *	@return amount
	 */
	public BigDecimal getApprovalAmt()
	{
		return getJP_ContractDocAmt();
	}	//	getApprovalAmt

	
	@Override
	public int customizeValidActions(String docStatus, Object processing, String orderType, String isSOTrx,
			int AD_Table_ID, String[] docAction, String[] options, int index) 
	{
		if(docStatus.equals(DocAction.STATUS_Completed))
		{
			index = 0; //initialize the index
			options[index++] = DocumentEngine.ACTION_Close;
			options[index++] = DocumentEngine.ACTION_Void;
			options[index++] = DocumentEngine.ACTION_ReActivate;
			return index;
		}

		if(docStatus.equals(DocAction.STATUS_Drafted))
		{
			index = 0; //initialize the index
			options[index++] = DocumentEngine.ACTION_Prepare;
			options[index++] = DocumentEngine.ACTION_Void;
			options[index++] = DocumentEngine.ACTION_Complete;
			return index;
		}

		return index;
	}

	@Override
	protected boolean beforeSave(boolean newRecord) 
	{
		//Check Contract Type and COntract Category
		if(newRecord || (is_ValueChanged("JP_ContractType") || is_ValueChanged("JP_ContractCategory_ID") || is_ValueChanged("JP_ContractT_ID")  ))
		{
			MContractT contractTemplate = MContractT.get(getCtx(), getJP_ContractT_ID());
			if(!contractTemplate.getJP_ContractType().equals(getJP_ContractType())
					|| contractTemplate.getJP_ContractCategory_ID() != getJP_ContractCategory_ID()
					|| contractTemplate.getJP_ContractT_ID() != getJP_ContractT_ID() )
			{
				//Contract type or Contract category are different from Contract template.
				log.saveError("Error", Msg.getMsg(getCtx(), "JP_DifferentContractTypeOrCategory"));
				return false;
			}
		}

		
		//Check Valid JP_ContractPeriodDate_To
		if(( newRecord || is_ValueChanged("JP_ContractPeriodDate_To") ) && getJP_ContractPeriodDate_To()!=null )
		{
			if(getJP_ContractPeriodDate_To().compareTo(getJP_ContractPeriodDate_From()) <= 0 )
			{
				log.saveError("Error", Msg.getMsg(getCtx(), "Invalid") + Msg.getElement(getCtx(), "JP_ContractPeriodDate_To"));
				return false;
			}
		}
		
		
		// Check Automatic Update Info
		if(isAutomaticUpdateJP())
		{
			if(getJP_ContractPeriodDate_To() == null)
				log.saveError("Error", Msg.getMsg(getCtx(), "FillMandatory") + Msg.getElement(getCtx(), "JP_ContractPeriodDate_To"));
			
			if(getJP_ContractCancelTerm_ID() == 0)
				log.saveError("Error", Msg.getMsg(getCtx(), "FillMandatory") + Msg.getElement(getCtx(), "JP_ContractCancelTerm_ID"));
			
			if(getJP_ContractExtendPeriod_ID() == 0)
				log.saveError("Error", Msg.getMsg(getCtx(), "FillMandatory") + Msg.getElement(getCtx(), "JP_ContractExtendPeriod_ID"));
		
			//Set JP_ContractPeriodDate_To
			if(( newRecord || is_ValueChanged("JP_ContractCancelDate") ) && getJP_ContractCancelDate() != null )
			{
				setJP_ContractPeriodDate_To(getJP_ContractCancelDate());
			}
			
			//Set Contract Cancel Deadline
			if(( newRecord || is_ValueChanged("JP_ContractPeriodDate_To")) || getJP_ContractCancelDeadline() == null )
			{
				MContractCancelTerm m_ContractCancelTerm = MContractCancelTerm.get(getCtx(), getJP_ContractCancelTerm_ID());
				setJP_ContractCancelDeadline(m_ContractCancelTerm.calculateCancelDeadLine(getJP_ContractPeriodDate_To()));
			}
			
			if(newRecord || is_ValueChanged("JP_ContractPeriodDate_To") || is_ValueChanged("JP_ContractCancelDeadline"))
			{
				if(getJP_ContractPeriodDate_To().compareTo(getJP_ContractCancelDeadline()) < 0)
				{
					log.saveError("Error", Msg.getMsg(getCtx(), "Invalid") + Msg.getElement(getCtx(), "JP_ContractCancelDeadline"));
					return false;
				}
			}
			
		}else{ 
			
			//Refresh Automatic update info
			setJP_ContractExtendPeriod_ID(0);
			setJP_ContractCancelDeadline(null);
		}
		
		
		//Check Contract Status
		updateContractStatus(DocAction.ACTION_None);
		
		return true;
	}
	
	private void updateContractStatus(String docAction)
	{
		if(getDocStatus().equals(DocAction.STATUS_Closed)
				|| getDocStatus().equals(DocAction.STATUS_Voided))
			return ;
		
		if(docAction.equals(DocAction.ACTION_Complete) || 
				(docAction.equals(DocAction.ACTION_None) && getDocStatus().equals(DocAction.STATUS_Completed)) )
		{
			Timestamp now = new Timestamp(System.currentTimeMillis());
			
			if(now.compareTo(getJP_ContractPeriodDate_From()) > 0 )
			{
				
				if(getJP_ContractPeriodDate_To()==null || now.compareTo(getJP_ContractPeriodDate_To()) < 0)
				{
					if(getJP_ContractStatus().equals(MContract.JP_CONTRACTSTATUS_Prepare))
					{
						setJP_ContractStatus(MContract.JP_CONTRACTSTATUS_UnderContract);
						setJP_ContractStatus_UC_Date(now);
					}
					setJP_ContractStatus_EC_Date(null);

				}else{
					setJP_ContractStatus(MContract.JP_CONTRACTSTATUS_ExpirationOfContract);
					setJP_ContractStatus_EC_Date(now);
				}
				
			}else{
				setJP_ContractStatus(MContract.JP_CONTRACTSTATUS_Prepare);
				setJP_ContractStatus_UC_Date(null);
				setJP_ContractStatus_EC_Date(null);
			}
			
		}else if(docAction.equals(DocAction.ACTION_Close)) {
			
			setJP_ContractStatus(MContract.JP_CONTRACTSTATUS_ExpirationOfContract);
			setJP_ContractStatus_EC_Date(new Timestamp (System.currentTimeMillis()));
			
		}else if(docAction.equals(DocAction.ACTION_Void)) {
			
			setJP_ContractStatus(MContract.JP_CONTRACTSTATUS_Invalid);
			setJP_ContractStatus_IN_Date(new Timestamp (System.currentTimeMillis()));
		}
		
	}//contractStatusUpdate
	
	
	
	private MContractContent[] m_ContractContents = null;
	
	public MContractContent[] getContractContents (String whereClause, String orderClause)
	{
		StringBuilder whereClauseFinal = new StringBuilder(MContractContent.COLUMNNAME_JP_Contract_ID+"=? ");
		if (!Util.isEmpty(whereClause, true))
			whereClauseFinal.append(whereClause);
		if (orderClause.length() == 0)
			orderClause = MContractContent.COLUMNNAME_JP_ContractContent_ID;
		//
		List<MContractContent> list = new Query(getCtx(), MContractContent.Table_Name, whereClauseFinal.toString(), get_TrxName())
										.setParameters(get_ID())
										.setOrderBy(orderClause)
										.list();
	
		return list.toArray(new MContractContent[list.size()]);		

	}
	
	public MContractContent[] getContractContents(boolean requery, String orderBy)
	{
		if (m_ContractContents != null && !requery) {
			set_TrxName(m_ContractContents, get_TrxName());
			return m_ContractContents;
		}
		//
		String orderClause = "";
		if (orderBy != null && orderBy.length() > 0)
			orderClause += orderBy;
		else
			orderClause += "JP_ContractContent_ID";
		m_ContractContents = getContractContents(null, orderClause);
		return m_ContractContents;
	}
	
	public MContractContent[] getContractContents()
	{
		return getContractContents(false, null);
	}

	/**	Cache				*/
	private static CCache<Integer,MContract>	s_cache = new CCache<Integer,MContract>(Table_Name, 20);
	
	/**
	 * 	Get from Cache
	 *	@param ctx context
	 *	@param JP_Contract_ID id
	 *	@return Contract Calender
	 */
	public static MContract get (Properties ctx, int JP_Contract_ID)
	{
		Integer ii = new Integer (JP_Contract_ID);
		MContract retValue = (MContract)s_cache.get(ii);
		if (retValue != null)
			return retValue;
		retValue = new MContract (ctx, JP_Contract_ID, null);
		if (retValue.get_ID () != 0)
			s_cache.put (JP_Contract_ID, retValue);
		return retValue;
	}	//	get
	
}	//	MContract
