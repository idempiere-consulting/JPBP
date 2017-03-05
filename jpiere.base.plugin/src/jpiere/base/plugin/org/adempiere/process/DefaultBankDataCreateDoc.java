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
package jpiere.base.plugin.org.adempiere.process;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.logging.Level;

import org.compiere.model.MBankStatement;
import org.compiere.model.MBankStatementLine;
import org.compiere.model.MInvoice;
import org.compiere.model.MPayment;
import org.compiere.model.PO;
import org.compiere.process.SvrProcess;
import org.compiere.util.AdempiereSystemError;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Util;

import jpiere.base.plugin.org.adempiere.model.MBankData;
import jpiere.base.plugin.org.adempiere.model.MBankDataLine;
import jpiere.base.plugin.org.adempiere.model.MBankDataSchema;
import jpiere.base.plugin.org.adempiere.model.MBill;

/**
 * JPIERE-0308 : Default Bank Data create Doc
 * 
 * @author 
 *
 */
public class DefaultBankDataCreateDoc extends SvrProcess {
	
	private int p_JP_BankData_ID = 0;
	private MBankData m_BankData = null;
	
	private MBankDataSchema BDSchema = null;
	
	private int p_AD_Client_ID = 0;

	@Override
	protected void prepare()
	{
		p_AD_Client_ID = getAD_Client_ID();
		p_JP_BankData_ID = getRecord_ID();
		m_BankData = new MBankData(getCtx(), p_JP_BankData_ID, get_TrxName());
		BDSchema = new MBankDataSchema(getCtx(), m_BankData.getJP_BankDataSchema_ID(), get_TrxName());		
	}
	
	@Override
	protected String doIt() throws Exception 
	{
		MBankStatement bs = new MBankStatement(getCtx(), 0, get_TrxName());
		MBankDataLine[] lines =  m_BankData.getLines();
		for(int i = 0 ; i < lines.length; i++)
		{
			String erroMsg = checkMatchedBankData(lines[i]);
			if(!Util.isEmpty(erroMsg))
				throw new Exception(erroMsg + Msg.getElement(getCtx(), "Line") + " : " +lines[i].getLine());
			
			if(i == 0)
			{
				PO.copyValues(m_BankData, bs);
				bs.setAD_Org_ID(m_BankData.getAD_Org_ID());
				bs.saveEx(get_TrxName());
				
				m_BankData.setC_BankStatement_ID(bs.getC_BankStatement_ID());
				m_BankData.saveEx(get_TrxName());
			}
			
			MBankStatementLine bsl = new MBankStatementLine(getCtx(), 0, get_TrxName());
			PO.copyValues(lines[i], bsl);
			bsl.setC_BankStatement_ID(bs.getC_BankStatement_ID());
			bsl.setAD_Org_ID(bs.getAD_Org_ID());
			bsl.setC_Currency_ID(m_BankData.getC_BankAccount().getC_Currency_ID());
			bsl.setStmtAmt(lines[i].getStmtAmt());
			bsl.setTrxAmt(lines[i].getTrxAmt());
			bsl.setChargeAmt(lines[i].getChargeAmt());
			bsl.setC_Charge_ID(lines[i].getC_Charge_ID());
			bsl.set_ValueNoCheck("C_Tax_ID", lines[i].get_Value("C_Tax_ID"));
			bsl.setInterestAmt(lines[i].getInterestAmt());
			bsl.setC_BPartner_ID(lines[i].getC_BPartner_ID());
			bsl.saveEx(get_TrxName());
			
			if(lines[i].getC_Invoice_ID() > 0)
			{
				MPayment payment = createPayment(bsl);
				if(!Util.isEmpty(BDSchema.getJP_Payment_DocAction()))
				{
					payment.processIt(BDSchema.getJP_Payment_DocAction());
					payment.saveEx(get_TrxName());
				}
				
			}else if(lines[i].getJP_Bill_ID() > 0){
				MPayment payment = createPayment(bsl);
				payment.set_ValueNoCheck("JP_Bill_ID", lines[i].getJP_Bill_ID());
				payment.saveEx(get_TrxName());
				if(!Util.isEmpty(BDSchema.getJP_Payment_DocAction()))
				{
					payment.processIt(BDSchema.getJP_Payment_DocAction());
					payment.saveEx(get_TrxName());
				}			
			}else if(lines[i].getC_Payment_ID() > 0){
				bsl.setC_Payment_ID(lines[i].getC_Payment_ID());
			}
			
			lines[i].setC_BankStatementLine_ID(bsl.getC_BankStatementLine_ID());
			lines[i].saveEx(get_TrxName());
		}

		if(!Util.isEmpty(BDSchema.getJP_BankStmt_DocAction()))
		{
			bs.processIt(BDSchema.getJP_BankStmt_DocAction());
			bs.saveEx(get_TrxName());
		}
		
		return null;
	}
	
	private String checkMatchedBankData(MBankDataLine bankDataLine)
	{
		
		if(bankDataLine.getC_Invoice_ID() > 0)
		{
			MInvoice invoice = new MInvoice(getCtx(), bankDataLine.getC_Invoice_ID(), null);
			if(invoice.isPaid())
			{
				log.saveError("JP_InvoicePaid","");//Invoice have paid already
				return Msg.getElement(getCtx(), "JP_InvoicePaid");
			}
		}
		
		
		if(bankDataLine.getJP_Bill_ID() > 0)
		{
			MBill bill = new MBill(getCtx(), bankDataLine.getJP_Bill_ID(), null);
			BigDecimal currentOpenAmt =  bill.getCurrentOpenAmt();
			if(!(currentOpenAmt.compareTo(Env.ZERO) > 0))
			{
				log.saveError("JP_BillPaid","");//Bill have paid already
				return Msg.getElement(getCtx(), "JP_BillPaid");			
			}
		}
		
		
		if(bankDataLine.getC_Payment_ID() > 0)
		{
			MPayment payment = new MPayment(getCtx(), bankDataLine.getC_Payment_ID(), null);
			if(payment.isReconciled())
			{
				log.saveError("JP_PaymentReconciled","");//Payment have reconciled already
				return Msg.getElement(getCtx(), "JP_PaymentReconciled");
			}
		}		
		
		return "";
	}
	
	
	/**
	 * 	Create Payment for BankStatement
	 *	@param bsl bank statement Line
	 *	@return Message
	 *  @throws Exception if not successful
	 */
	private MPayment createPayment (MBankStatementLine bsl) throws Exception
	{
		if (bsl == null || bsl.getC_Payment_ID() != 0)
			return null;
		if (log.isLoggable(Level.FINE)) log.fine(bsl.toString());
		if (bsl.getC_Invoice_ID() == 0 && bsl.getC_BPartner_ID() == 0)
			throw new AdempiereUserError ("@NotFound@ @C_Invoice_ID@ / @C_BPartner_ID@");
		//
		MBankStatement bs = new MBankStatement (getCtx(), bsl.getC_BankStatement_ID(), get_TrxName());
		//
		MPayment payment = createPayment (bsl.getC_Invoice_ID(), bsl.getC_BPartner_ID(),
			bsl.getC_Currency_ID(), bsl.getStmtAmt(), bsl.getTrxAmt(), 
			bs.getC_BankAccount_ID(), bsl.getStatementLineDate(), bsl.getDateAcct(),
			bsl.getDescription(), bsl.getAD_Org_ID());
		if (payment == null)
			throw new AdempiereSystemError("Could not create Payment");
		//	update statement
		bsl.setPayment(payment);
		bsl.saveEx();
		//
		return payment;
	}	//	createPayment
	
	
	
	/**
	 * 	Create actual Payment
	 *	@param C_Invoice_ID invoice
	 *	@param C_BPartner_ID partner ignored when invoice exists
	 *	@param C_Currency_ID currency
	 *	@param StmtAmt statement amount
	 *	@param TrxAmt transaction amt
	 *	@param C_BankAccount_ID bank account
	 *	@param DateTrx transaction date
	 *	@param DateAcct	accounting date
	 *	@param Description description
	 *	@param AD_Org_ID org
	 *	@return payment
	 */
	private MPayment createPayment (int C_Invoice_ID, int C_BPartner_ID, 
		int C_Currency_ID, BigDecimal StmtAmt, BigDecimal TrxAmt,
		int C_BankAccount_ID, Timestamp DateTrx, Timestamp DateAcct, 
		String Description, int AD_Org_ID)
	{
		//	Trx Amount = Payment overwrites Statement Amount if defined
		BigDecimal PayAmt = TrxAmt;
		if (PayAmt == null || Env.ZERO.compareTo(PayAmt) == 0)
			PayAmt = StmtAmt;
		if (C_Invoice_ID == 0
			&& (PayAmt == null || Env.ZERO.compareTo(PayAmt) == 0))
			throw new IllegalStateException ("@PayAmt@ = 0");
		if (PayAmt == null)
			PayAmt = Env.ZERO;
		//
		MPayment payment = new MPayment (getCtx(), 0, get_TrxName());
		payment.setAD_Org_ID(AD_Org_ID);
		payment.setC_BankAccount_ID(C_BankAccount_ID);
		payment.setTenderType(MPayment.TENDERTYPE_Check);
		if (DateTrx != null)
			payment.setDateTrx(DateTrx);
		else if (DateAcct != null)
			payment.setDateTrx(DateAcct);
		if (DateAcct != null)
			payment.setDateAcct(DateAcct);
		else
			payment.setDateAcct(payment.getDateTrx());
		payment.setDescription(Description);
		//
		if (C_Invoice_ID != 0)
		{
			MInvoice invoice = new MInvoice (getCtx(), C_Invoice_ID, null);
			payment.setC_DocType_ID(invoice.isSOTrx());		//	Receipt
			payment.setC_Invoice_ID(invoice.getC_Invoice_ID());
			payment.setC_BPartner_ID (invoice.getC_BPartner_ID());
			if (PayAmt.signum() != 0)	//	explicit Amount
			{
				payment.setC_Currency_ID(C_Currency_ID);
				if (invoice.isSOTrx())
					payment.setPayAmt(PayAmt);
				else	//	payment is likely to be negative
					payment.setPayAmt(PayAmt.negate());
				payment.setOverUnderAmt(invoice.getOpenAmt().subtract(payment.getPayAmt()));
			}
			else	// set Pay Amout from Invoice
			{
				payment.setC_Currency_ID(invoice.getC_Currency_ID());
				payment.setPayAmt(invoice.getOpenAmt());
			}
		}
		else if (C_BPartner_ID != 0)
		{
			payment.setC_BPartner_ID(C_BPartner_ID);
			payment.setC_Currency_ID(C_Currency_ID);
			if (PayAmt.signum() < 0)	//	Payment
			{
				payment.setPayAmt(PayAmt.abs());
				payment.setC_DocType_ID(false);
			}
			else	//	Receipt
			{
				payment.setPayAmt(PayAmt);
				payment.setC_DocType_ID(true);
			}
		}
		else
			return null;
		payment.saveEx();
		//
		if (!payment.processIt(MPayment.DOCACTION_Complete)) {
			log.warning("Payment Process Failed: " + payment.getDocumentNo() + " " + payment.getProcessMsg());
			throw new IllegalStateException("Payment Process Failed: " + payment.getDocumentNo() + " " + payment.getProcessMsg());
			
		}
		payment.saveEx();
		return payment;		
	}	//	createPayment
}
