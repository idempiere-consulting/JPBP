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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;

import org.adempiere.exceptions.DBException;
import org.adempiere.model.ImportValidator;
import org.adempiere.process.ImportProcess;
import org.adempiere.util.IProcessUI;
import org.compiere.model.MProcess;
import org.compiere.model.MProduct;
import org.compiere.model.MProductPrice;
import org.compiere.model.MTable;
import org.compiere.model.MTableIndex;
import org.compiere.model.ModelValidationEngine;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;

import jpiere.base.plugin.org.adempiere.model.X_I_ProductJP;

/**
 *	Import Products from I_ProductJP
 *
 * 	@author 	Jorg Janke
 * 	@version 	$Id: ImportProduct.java,v 1.3 2006/07/30 00:51:01 jjanke Exp $
 *
 *  @author Carlos Ruiz, globalqss
 * 			<li>FR [ 2788278 ] Data Import Validator - migrate core processes
 * 				https://sourceforge.net/tracker/?func=detail&aid=2788278&group_id=176962&atid=879335
 *
 *  @author Hideaki Hagiwara
 */
public class JPiereImportProduct extends SvrProcess implements ImportProcess
{
	/**	Client to be imported to		*/
	private int				m_AD_Client_ID = 0;
	/**	Delete old Imported				*/
	private boolean			m_deleteOldImported = false;

	/** Effective						*/
	private Timestamp		m_DateValue = null;
	/** Pricelist to Update				*/
	private int 			p_M_PriceList_Version_ID = 0;

	private boolean			isRecordCommitJP =true;

	private boolean 		isDeleteIndexJP = false;

	private boolean			isInvalidConstraintJP = false;

	private boolean			isMonitoringProcessJP = true;

	private boolean			isUniqueCheckJP = false;

	private String[] tables = new String[] {
			"M_Product",
			"M_Product_PO"
	};

	/**
	 *  Prepare - e.g., get Parameters.
	 */
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (name.equals("AD_Client_ID"))
				m_AD_Client_ID = ((BigDecimal)para[i].getParameter()).intValue();
			else if (name.equals("DeleteOldImported"))
				m_deleteOldImported = "Y".equals(para[i].getParameter());
			else if (name.equals("M_PriceList_Version_ID"))
				p_M_PriceList_Version_ID = para[i].getParameterAsInt();
			else if (name.equals("IsRecordCommitJP"))
				isRecordCommitJP = "Y".equals(para[i].getParameter());
			else if (name.equals("IsDeleteIndexJP"))
				isDeleteIndexJP = "Y".equals(para[i].getParameter());
			else if (name.equals("IsInvalidConstraintJP"))
				isInvalidConstraintJP = "Y".equals(para[i].getParameter());
			else if (name.equals("IsMonitoringProcessJP"))
				isMonitoringProcessJP = "Y".equals(para[i].getParameter());
			else if (name.equals("IsUniqueCheckJP"))
				isUniqueCheckJP = "Y".equals(para[i].getParameter());
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
		if (m_DateValue == null)
			m_DateValue = new Timestamp (System.currentTimeMillis());
	}	//	prepare

	//	Field to copy From Product if Import does not have value
	private String[] strFieldsToCopy = new String[] {
			"Value",
			"Name",
			"Description",
			"DocumentNote",
			"Help",
			"UPC",
			"SKU",
			"Classification",
			"ProductType",
			"Discontinued",
			"DiscontinuedBy",
			"DiscontinuedAt",
			"ImageURL",
			"DescriptionURL"
		};

	/**
	 *  Perform process.
	 *  @return Message
	 *  @throws Exception
	 */
	protected String doIt() throws java.lang.Exception
	{
		//処理の計測
		long start = System.currentTimeMillis();


		//プロセス状況のモニタリング
		IProcessUI processMonitor = null;
		if(isMonitoringProcessJP)
		{
			processMonitor = Env.getProcessUI(getCtx());
		}

		//lock
		int AD_Process_ID = MProcess.getProcess_ID("JP_Import_Product", get_TrxName());
		MProcess process = new MProcess(getCtx(), AD_Process_ID,get_TrxName());
		boolean isOK = DB.getDatabase().forUpdate(process, 5);
		if(!isOK)
		{
			throw new DBException("Could not lock records");
		}

		StringBuilder sql = null;
		int no = 0;
		String clientCheck = getWhereClause();

		//	****	Prepare	****

		if (processMonitor != null)	processMonitor.statusUpdate("インポート済みのデータの削除");
		if (m_deleteOldImported)
		{
			sql = new StringBuilder ("DELETE I_ProductJP ")
				.append("WHERE I_IsImported='Y'").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (log.isLoggable(Level.INFO)) log.info("Delete Old Imported =" + no);
		}

		if (processMonitor != null)	processMonitor.statusUpdate("組織、更新情報、品目タイプの設定");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET AD_Client_ID = COALESCE (AD_Client_ID, ").append(m_AD_Client_ID).append("),")
			.append(" AD_Org_ID = COALESCE (AD_Org_ID, 0),")
			.append(" IsActive = COALESCE (IsActive, 'Y'),")
			.append(" Created = COALESCE (Created, SysDate),")
			.append(" CreatedBy = COALESCE (CreatedBy, 0),")
			.append(" Updated = COALESCE (Updated, SysDate),")
			.append(" UpdatedBy = COALESCE (UpdatedBy, 0),")
			.append(" ProductType = COALESCE (ProductType, 'I'),")
			.append(" I_ErrorMsg = ' ',")
			.append(" I_IsImported = 'N' ")
			.append("WHERE I_IsImported<>'Y' OR I_IsImported IS NULL");
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("Reset=" + no);


		ModelValidationEngine.get().fireImportValidate(this, null, null, ImportValidator.TIMING_BEFORE_VALIDATE);

		if (processMonitor != null)	processMonitor.statusUpdate("取引先マスタを取引先の検索キーから逆引きする処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET C_BPartner_ID=(SELECT C_BPartner_ID FROM C_BPartner p")
			.append(" WHERE i.BPartner_Value=p.Value AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append("WHERE C_BPartner_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("BPartner=" + no);
		//取引先の検索キーから逆引きできなかった場合の処理
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid BPartner,' ")
			.append("WHERE C_BPartner_ID IS NULL AND BPartner_Value IS NOT NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("Invalid BPartner=" + no);


		//	****	Find Product
		if (processMonitor != null)	processMonitor.statusUpdate("UPCから品目マスタを逆引きする処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product p")
			.append(" WHERE i.UPC=p.UPC AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append("WHERE M_Product_ID IS NULL")
			.append(" AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("Product Existing UPC=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("品目マスタを検索キーから逆引きする処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product p")
			.append(" WHERE i.Value=p.Value AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append("WHERE M_Product_ID IS NULL")
			.append(" AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("Product Existing Value=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("品目マスタを仕入先の品目コードから逆引きする処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET M_Product_ID=(SELECT M_Product_ID FROM M_Product_po p")
			.append(" WHERE i.C_BPartner_ID=p.C_BPartner_ID")
			.append(" AND i.VendorProductNo=p.VendorProductNo AND i.AD_Client_ID=p.AD_Client_ID) ")
			.append("WHERE M_Product_ID IS NULL")
			.append(" AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("Product Existing Vendor ProductNo=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("新規登録の品目マスタで品目カテゴリが指定されていなかった場合の処理");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET ProductCategory_Value=(SELECT MAX(Value) FROM M_Product_Category")
			.append(" WHERE IsDefault='Y' AND AD_Client_ID=").append(m_AD_Client_ID).append(") ")
			.append("WHERE ProductCategory_Value IS NULL AND M_Product_Category_ID IS NULL")
			.append(" AND M_Product_ID IS NULL")	//	set category only if product not found
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.FINE)) log.fine("Set Category Default Value=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("品目カテゴリを品目カテゴリの検索キーから逆引きする処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET M_Product_Category_ID=(SELECT M_Product_Category_ID FROM M_Product_Category c")
			.append(" WHERE i.ProductCategory_Value=c.Value AND i.AD_Client_ID=c.AD_Client_ID) ")
			.append("WHERE ProductCategory_Value IS NOT NULL AND M_Product_Category_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("Set Category=" + no);


		//	Copy From Product if Import does not have value
		if (processMonitor != null)	processMonitor.statusUpdate("更新処理の際に、更新データが無いテキスト項目は既存のデータより値をコピーする");
		for (int i = 0; i < strFieldsToCopy.length; i++)
		{
			sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET ").append(strFieldsToCopy[i]).append(" = (SELECT ").append(strFieldsToCopy[i]).append(" FROM M_Product p")
				.append(" WHERE i.M_Product_ID=p.M_Product_ID AND i.AD_Client_ID=p.AD_Client_ID) ")
				.append("WHERE M_Product_ID IS NOT NULL")
				.append(" AND ").append(strFieldsToCopy[i]).append(" IS NULL")
				.append(" AND I_IsImported='N'").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (no != 0)
				if (log.isLoggable(Level.FINE)) log.fine(strFieldsToCopy[i] + " - default from existing Product=" + no);
		}

		if (processMonitor != null)	processMonitor.statusUpdate("更新処理の際に、更新データが無い数値項目は既存のデータより値をコピーする");
		String[] numFields = new String[] {"C_UOM_ID","M_Product_Category_ID",
			"Volume","Weight","ShelfWidth","ShelfHeight","ShelfDepth","UnitsPerPallet"};
		for (int i = 0; i < numFields.length; i++)
		{
			sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET ").append(numFields[i]).append(" = (SELECT ").append(numFields[i]).append(" FROM M_Product p")
				.append(" WHERE i.M_Product_ID=p.M_Product_ID AND i.AD_Client_ID=p.AD_Client_ID) ")
				.append("WHERE M_Product_ID IS NOT NULL")
				.append(" AND (").append(numFields[i]).append(" IS NULL OR ").append(numFields[i]).append("=0)")
				.append(" AND I_IsImported='N'").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (no != 0)
				if (log.isLoggable(Level.FINE)) log.fine(numFields[i] + " default from existing Product=" + no);
		}

		if (processMonitor != null)	processMonitor.statusUpdate("更新処理の際に、更新データが無いテキスト項目を既存の購買情報より値をコピーする");
		String[] strFieldsPO = new String[] {"UPC",
			"PriceEffective","VendorProductNo","VendorCategory","Manufacturer",
			"Discontinued","DiscontinuedBy", "DiscontinuedAt"};
		for (int i = 0; i < strFieldsPO.length; i++)
		{
			sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET ").append(strFieldsPO[i]).append(" = (SELECT ").append(strFieldsPO[i])
				.append(" FROM M_Product_PO p")
				.append(" WHERE i.M_Product_ID=p.M_Product_ID AND i.C_BPartner_ID=p.C_BPartner_ID AND i.AD_Client_ID=p.AD_Client_ID) ")
				.append("WHERE M_Product_ID IS NOT NULL AND C_BPartner_ID IS NOT NULL")
				.append(" AND ").append(strFieldsPO[i]).append(" IS NULL")
				.append(" AND I_IsImported='N'").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (no != 0)
				if (log.isLoggable(Level.FINE)) log.fine(strFieldsPO[i] + " default from existing Product PO=" + no);
		}

		if (processMonitor != null)	processMonitor.statusUpdate("更新処理の際に、更新データが無い数値項目を既存の購買情報より値をコピーする");
		String[] numFieldsPO = new String[] {"C_UOM_ID","C_Currency_ID",
			"PriceList","PricePO","RoyaltyAmt",
			"Order_Min","Order_Pack","CostPerOrder","DeliveryTime_Promised"};
		for (int i = 0; i < numFieldsPO.length; i++)
		{
			sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET ").append(numFieldsPO[i]).append(" = (SELECT ").append(numFieldsPO[i])
				.append(" FROM M_Product_PO p")
				.append(" WHERE i.M_Product_ID=p.M_Product_ID AND i.C_BPartner_ID=p.C_BPartner_ID AND i.AD_Client_ID=p.AD_Client_ID) ")
				.append("WHERE M_Product_ID IS NOT NULL AND C_BPartner_ID IS NOT NULL")
				.append(" AND (").append(numFieldsPO[i]).append(" IS NULL OR ").append(numFieldsPO[i]).append("=0)")
				.append(" AND I_IsImported='N'").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (no != 0)
				if (log.isLoggable(Level.FINE)) log.fine(numFieldsPO[i] + " default from existing Product PO=" + no);
		}

		if (processMonitor != null)	processMonitor.statusUpdate("品目カテゴリが無かった場合のエラー処理");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid ProdCategory,' ")
			.append("WHERE M_Product_Category_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("Invalid Category=" + no);


		if (processMonitor != null)	processMonitor.statusUpdate("インポートデータにUOMを設定する処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET X12DE355 = ")
			.append("(SELECT MAX(X12DE355) FROM C_UOM u WHERE u.IsDefault='Y' AND u.AD_Client_ID IN (0,i.AD_Client_ID)) ")
			.append("WHERE X12DE355 IS NULL AND C_UOM_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.FINE)) log.fine("Set UOM Default=" + no);

		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET C_UOM_ID = (SELECT C_UOM_ID FROM C_UOM u WHERE u.X12DE355=i.X12DE355 AND u.AD_Client_ID IN (0,i.AD_Client_ID)) ")
			.append("WHERE C_UOM_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("Set UOM=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("UOMが無かった場合のエラー処理");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid UOM, ' ")
			.append("WHERE C_UOM_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("Invalid UOM=" + no);


		if (processMonitor != null)	processMonitor.statusUpdate("インポートデータに通貨を設定する処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET ISO_Code=(SELECT ISO_Code FROM C_Currency c")
			.append(" INNER JOIN C_AcctSchema a ON (a.C_Currency_ID=c.C_Currency_ID)")
			.append(" INNER JOIN AD_ClientInfo ci ON (a.C_AcctSchema_ID=ci.C_AcctSchema1_ID)")
			.append(" WHERE ci.AD_Client_ID=i.AD_Client_ID) ")
			.append("WHERE C_Currency_ID IS NULL AND ISO_Code IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.FINE)) log.fine("Set Currency Default=" + no);
		//
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET C_Currency_ID=(SELECT C_Currency_ID FROM C_Currency c")
			.append(" WHERE i.ISO_Code=c.ISO_Code AND c.AD_Client_ID IN (0,i.AD_Client_ID)) ")
			.append("WHERE C_Currency_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("doIt- Set Currency=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("通貨が無かった場合のエラー処理");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Currency,' ")
			.append("WHERE C_Currency_ID IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("Invalid Currency=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("品目タイプが不適切だった場合のエラー処理");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Invalid ProductType,' ")
			.append("WHERE ProductType NOT IN ('E','I','R','S','A')")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("Invalid ProductType=" + no);

		//	Unique UPC/Value
		if(isUniqueCheckJP)//処理が非常に重いのでフラグにより実行するかどうかを判断する。
		{
			if (processMonitor != null)	processMonitor.statusUpdate("インポートする品目マスタの検索キーがユニークで無かった場合のエラー処理");
			sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=Value not unique,' ")
				.append("WHERE I_IsImported<>'Y'")
				.append(" AND Value IN (SELECT Value FROM I_ProductJP ii WHERE i.AD_Client_ID=ii.AD_Client_ID GROUP BY Value HAVING COUNT(*) > 1)").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (no != 0)
				log.warning("Not Unique Value=" + no);
			//
			if (processMonitor != null)	processMonitor.statusUpdate("インポートする品目マスタのUPCがユニークで無かった場合のエラー処理");
			sql = new StringBuilder ("UPDATE I_ProductJP i ")
				.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=UPC not unique,' ")
				.append("WHERE I_IsImported<>'Y'")
				.append(" AND UPC IN (SELECT UPC FROM I_ProductJP ii WHERE i.AD_Client_ID=ii.AD_Client_ID GROUP BY UPC HAVING COUNT(*) > 1)").append(clientCheck);
			no = DB.executeUpdate(sql.toString(), get_TrxName());
			if (no != 0)
				log.warning("Not Unique UPC=" + no);
		}

		//	Mandatory Value
		if (processMonitor != null)	processMonitor.statusUpdate("インポートする品目マスタの検索キーが無かった場合のエラー処理");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=No Mandatory Value,' ")
			.append("WHERE Value IS NULL")
			.append(" AND I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("No Mandatory Value=" + no);

		//	Vendor Product No
		if (processMonitor != null)	processMonitor.statusUpdate("インポートする品目マスタの情報に仕入先の品目コードが無かった場合に、検索キーを設定する処理");
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET VendorProductNo=Value ")
			.append("WHERE C_BPartner_ID IS NOT NULL AND VendorProductNo IS NULL")
			.append(" AND I_IsImported='N'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (log.isLoggable(Level.INFO)) log.info("VendorProductNo Set to Value=" + no);

		if (processMonitor != null)	processMonitor.statusUpdate("インポートする品目マスタの情報に仕入先の品目コードの重複チェック");
		sql = new StringBuilder ("UPDATE I_ProductJP i ")
			.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||'ERR=VendorProductNo not unique,' ")
			.append("WHERE I_IsImported<>'Y'")
			.append(" AND C_BPartner_ID IS NOT NULL")
			.append(" AND (C_BPartner_ID, VendorProductNo) IN ")
			.append(" (SELECT C_BPartner_ID, VendorProductNo FROM I_ProductJP ii WHERE i.AD_Client_ID=ii.AD_Client_ID GROUP BY C_BPartner_ID, VendorProductNo HAVING COUNT(*) > 1)")
			.append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());
		if (no != 0)
			log.warning("Not Unique VendorProductNo=" + no);

		//	Get Default Tax Category
		int C_TaxCategory_ID = 0;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			StringBuilder dbpst = new StringBuilder("SELECT C_TaxCategory_ID FROM C_TaxCategory WHERE IsDefault='Y'").append(clientCheck);
			pstmt = DB.prepareStatement(dbpst.toString(), get_TrxName());
			rs = pstmt.executeQuery();
			if (rs.next())
				C_TaxCategory_ID = rs.getInt(1);
		}
		catch (SQLException e)
		{
			throw new Exception ("TaxCategory", e);
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		if (log.isLoggable(Level.FINE)) log.fine("C_TaxCategory_ID=" + C_TaxCategory_ID);

		ModelValidationEngine.get().fireImportValidate(this, null, null, ImportValidator.TIMING_AFTER_VALIDATE);

		commitEx();

		/**
		 * 前処理終了//TODO
		 */
		long endPreProcessing = System.currentTimeMillis();
		addLog("前処理時間: " + (endPreProcessing - start) + " ミリ秒");


		/**
		 * 制約の無効とIndexの削除//TODO
		 */
		//制約の無効
		if(isInvalidConstraintJP)
		{
			if (processMonitor != null)	processMonitor.statusUpdate("制約の無効化");
			long startInvalidConstraint = System.currentTimeMillis();

			for(int i = 0; i < tables.length; i++)
			{
				StringBuilder invalidConstraint = new StringBuilder (" update pg_trigger set tgenabled = 'D' "
												+ "where oid in (select tr.oid from pg_trigger tr INNER JOIN pg_class cl on (tr.tgrelid = cl.oid) WHERE cl.relname="
												+ "lower('" + tables[i] + "') )");

				DB.executeUpdate(invalidConstraint.toString(), get_TrxName());
			}
			commitEx();

			long endInvalidConstraint = System.currentTimeMillis();
			addLog("制約無効の処理時間: " + (endInvalidConstraint - startInvalidConstraint) + " ミリ秒");
		}

		//indexの削除
		if(isDeleteIndexJP)
		{
			if (processMonitor != null)	processMonitor.statusUpdate("Indexの削除");
			long startDeleteIndex = System.currentTimeMillis();

			for(int i = 0; i < tables.length; i++)
			{
				MTable mTable = MTable.get(getCtx(), tables[i]);
				MTableIndex[] indexes = MTableIndex.get(mTable);
				for(int j = 0; j < indexes.length; j++)
				{
					String indexDropSql = indexes[j].getDropDDL();
					int rvalue = DB.executeUpdateEx(indexDropSql, get_TrxName());
//					addLog(0, null, new BigDecimal(rvalue), indexDropSql.toString());
				}
			}
			commitEx();

			long endDeleteIndex = System.currentTimeMillis();
			addLog("Indexの削除時間: " + (endDeleteIndex - startDeleteIndex) + " ミリ秒");
		}


		//lock
		//TODO:lock出来るのはcommitするまで
//		sql = new StringBuilder ("SELECT * FROM I_ProductJP WHERE I_IsImported='N'")
//		.append(clientCheck)
//		.append(" FOR UPDATE ");
//
//		PreparedStatement lockPSTMT = null;
//		ResultSet lockRS = null;
//		try
//		{
//			lockPSTMT = DB.prepareStatement(sql.toString(),ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE, get_TrxName());
//			lockPSTMT.setQueryTimeout(10); //ロックタイムアウト秒
//			lockRS = lockPSTMT.executeQuery();
//		}
//		catch (SQLException e)
//		{
//			throw new DBException("Could not lock record for caused by " + e.getLocalizedMessage());
//		}
//		finally
//		{
//			DB.close(lockRS, lockPSTMT);
//			lockRS = null;lockPSTMT = null;
//		}

		//Rerock:ロックできるのはコミットまでなので再度ロックする。ロックできるのはコミットまでなので、レコード毎にコミットする場合はロックの意味はほとんどない
		if(!DB.getDatabase().forUpdate(process, 5))
		{
			throw new DBException("Could not lock records");
		}



		//	-------------------------------------------------------------------

		long startImport = System.currentTimeMillis();
		if (processMonitor != null)	processMonitor.statusUpdate("インポート処理開始");

		int noInsert = 0;
		int noUpdate = 0;
		int noInsertPO = 0;
		int noUpdatePO = 0;

		//	Go through Records
		log.fine("start inserting/updating ...");
		sql = new StringBuilder ("SELECT * FROM I_ProductJP WHERE I_IsImported='N'")
			.append(clientCheck)
			.append(" ORDER BY Value ");
		PreparedStatement pstmt_setImported = null;
		PreparedStatement pstmt_insertProductPO = null;
		try
		{
			/*	Insert Product from Import
			PreparedStatement pstmt_insertProduct = conn.prepareStatement
				("INSERT INTO M_Product (M_Product_ID,"
				+ "AD_Client_ID,AD_Org_ID,IsActive,Created,CreatedBy,Updated,UpdatedBy,"
				+ "Value,Name,Description,DocumentNote,Help,"
				+ "UPC,SKU,C_UOM_ID,IsSummary,M_Product_Category_ID,C_TaxCategory_ID,"
				+ "ProductType,ImageURL,DescriptionURL) "
				+ "SELECT ?,"
				+ "AD_Client_ID,AD_Org_ID,'Y',SysDate,CreatedBy,SysDate,UpdatedBy,"
				+ "Value,Name,Description,DocumentNote,Help,"
				+ "UPC,SKU,C_UOM_ID,'N',M_Product_Category_ID," + C_TaxCategory_ID + ","
				+ "ProductType,ImageURL,DescriptionURL "
				+ "FROM I_ProductJP "
				+ "WHERE I_ProductJP_ID=?");
			*/
			//	Update Product from Import
			//jz moved
			/*
			String sqlt = "UPDATE M_PRODUCT "
				+ "SET (Value,Name,Description,DocumentNote,Help,"
				+ "UPC,SKU,C_UOM_ID,M_Product_Category_ID,Classification,ProductType,"
				+ "Volume,Weight,ShelfWidth,ShelfHeight,ShelfDepth,UnitsPerPallet,"
				+ "Discontinued,DiscontinuedBy,Updated,UpdatedBy)= "
				+ "(SELECT Value,Name,Description,DocumentNote,Help,"
				+ "UPC,SKU,C_UOM_ID,M_Product_Category_ID,Classification,ProductType,"
				+ "Volume,Weight,ShelfWidth,ShelfHeight,ShelfDepth,UnitsPerPallet,"
				+ "Discontinued,DiscontinuedBy,SysDate,UpdatedBy"
				+ " FROM I_ProductJP WHERE I_ProductJP_ID=?) "
				+ "WHERE M_Product_ID=?";
			PreparedStatement pstmt_updateProduct = DB.prepareStatement
				(sqlt, get_TrxName());

			//	Update Product_PO from Import
			sqlt = "UPDATE M_Product_PO "
				+ "SET (IsCurrentVendor,C_UOM_ID,C_Currency_ID,UPC,"
				+ "PriceList,PricePO,RoyaltyAmt,PriceEffective,"
				+ "VendorProductNo,VendorCategory,Manufacturer,"
				+ "Discontinued,DiscontinuedBy,Order_Min,Order_Pack,"
				+ "CostPerOrder,DeliveryTime_Promised,Updated,UpdatedBy)= "
				+ "(SELECT 'Y',C_UOM_ID,C_Currency_ID,UPC,"
				+ "PriceList,PricePO,RoyaltyAmt,PriceEffective,"
				+ "VendorProductNo,VendorCategory,Manufacturer,"
				+ "Discontinued,DiscontinuedBy,Order_Min,Order_Pack,"
				+ "CostPerOrder,DeliveryTime_Promised,SysDate,UpdatedBy"
				+ " FROM I_ProductJP"
				+ " WHERE I_ProductJP_ID=?) "
				+ "WHERE M_Product_ID=? AND C_BPartner_ID=?";
			PreparedStatement pstmt_updateProductPO = DB.prepareStatement
				(sqlt, get_TrxName());
*/
			//	Insert Product from Import
			pstmt_insertProductPO = DB.prepareStatement
				("INSERT INTO M_Product_PO (M_Product_ID,C_BPartner_ID, "
				+ "AD_Client_ID,AD_Org_ID,IsActive,Created,CreatedBy,Updated,UpdatedBy,"
				+ "IsCurrentVendor,C_UOM_ID,C_Currency_ID,UPC,"
				+ "PriceList,PricePO,RoyaltyAmt,PriceEffective,"
				+ "VendorProductNo,VendorCategory,Manufacturer,"
				+ "Discontinued,DiscontinuedBy, DiscontinuedAt, Order_Min,Order_Pack,"
				+ "CostPerOrder,DeliveryTime_Promised) "
				+ "SELECT ?,?, "
				+ "AD_Client_ID,AD_Org_ID,'Y',SysDate,CreatedBy,SysDate,UpdatedBy,"
				+ "'Y',C_UOM_ID,C_Currency_ID,UPC,"
				+ "PriceList,PricePO,RoyaltyAmt,PriceEffective,"
				+ "VendorProductNo,VendorCategory,Manufacturer,"
				+ "Discontinued,DiscontinuedBy, DiscontinuedAt, Order_Min,Order_Pack,"
				+ "CostPerOrder,DeliveryTime_Promised "
				+ "FROM I_ProductJP "
				+ "WHERE I_ProductJP_ID=?", get_TrxName());

			//	Set Imported = Y
			pstmt_setImported = DB.prepareStatement
				("UPDATE I_ProductJP SET I_IsImported='Y', M_Product_ID=?, "
				+ "Updated=SysDate, Processed='Y' WHERE I_ProductJP_ID=?", get_TrxName());

			//
			pstmt = DB.prepareStatement(sql.toString(), get_TrxName());
			rs = pstmt.executeQuery();
			while (rs.next())
			{
				X_I_ProductJP imp = new X_I_ProductJP(getCtx(), rs, get_TrxName());
				int I_ProductJP_ID = imp.getI_ProductJP_ID();
				int M_Product_ID = imp.getM_Product_ID();
				int C_BPartner_ID = imp.getC_BPartner_ID();
				boolean newProduct = M_Product_ID == 0;
				if (log.isLoggable(Level.FINE)) log.fine("I_ProductJP_ID=" + I_ProductJP_ID + ", M_Product_ID=" + M_Product_ID
					+ ", C_BPartner_ID=" + C_BPartner_ID);

				//処理の途中経過表示
				if (processMonitor != null)
				{
					processMonitor.statusUpdate(imp.getValue());
				}


				//	Product
				if (newProduct)			//	Insert new Product
				{
					MProduct product = new MProduct(getCtx(), 0, get_TrxName());
					product.setAD_Org_ID(imp.getAD_Org_ID());
//					product.setUpdatedBy(imp.getUpdatedBy());
					//
					product.setValue(imp.getValue());
					product.setName(imp.getName());
					product.setDescription(imp.getDescription());
					product.setDocumentNote(imp.getDocumentNote());
					product.setHelp(imp.getHelp());
					product.setUPC(imp.getUPC());
					product.setSKU(imp.getSKU());
					product.setC_UOM_ID(imp.getC_UOM_ID());
					product.setM_Product_Category_ID(imp.getM_Product_Category_ID());
					product.setProductType(imp.getProductType());
					product.setImageURL(imp.getImageURL());
					product.setDescriptionURL(imp.getDescriptionURL());
					product.setVolume(imp.getVolume());
					product.setWeight(imp.getWeight());


					product.setC_TaxCategory_ID(C_TaxCategory_ID);
					ModelValidationEngine.get().fireImportValidate(this, imp, product, ImportValidator.TIMING_AFTER_IMPORT);
					if (product.save())
					{
						M_Product_ID = product.getM_Product_ID();
						log.finer("Insert Product");
						noInsert++;
					}
					else
					{
						StringBuilder sql0 = new StringBuilder ("UPDATE I_ProductJP i ")
							.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||").append(DB.TO_STRING("Insert Product failed"))
							.append("WHERE I_ProductJP_ID=").append(I_ProductJP_ID);
						DB.executeUpdate(sql0.toString(), get_TrxName());
						continue;
					}
				}
				else					//	Update Product
				{
					StringBuilder sqlt = new StringBuilder("UPDATE M_PRODUCT ")
						.append("SET (Value,Name,Description,DocumentNote,Help,")
						.append("UPC,SKU,C_UOM_ID,M_Product_Category_ID,Classification,ProductType,")
						.append("Volume,Weight,ShelfWidth,ShelfHeight,ShelfDepth,UnitsPerPallet,")
						.append("Discontinued,DiscontinuedBy, DiscontinuedAt, Updated,UpdatedBy)= ")
						.append("(SELECT Value,Name,Description,DocumentNote,Help,")
						.append("UPC,SKU,C_UOM_ID,M_Product_Category_ID,Classification,ProductType,")
						.append("Volume,Weight,ShelfWidth,ShelfHeight,ShelfDepth,UnitsPerPallet,")
						.append("Discontinued,DiscontinuedBy, DiscontinuedAt, SysDate,UpdatedBy")
						.append(" FROM I_ProductJP WHERE I_ProductJP_ID=").append(I_ProductJP_ID).append(") ")
						.append("WHERE M_Product_ID=").append(M_Product_ID);
					PreparedStatement pstmt_updateProduct = DB.prepareStatement
						(sqlt.toString(), get_TrxName());

					//jz pstmt_updateProduct.setInt(1, I_ProductJP_ID);
					//   pstmt_updateProduct.setInt(2, M_Product_ID);
					try
					{
						no = pstmt_updateProduct.executeUpdate();
						if (log.isLoggable(Level.FINER)) log.finer("Update Product = " + no);
						noUpdate++;
					}
					catch (SQLException ex)
					{
						log.warning("Update Product - " + ex.toString());
						StringBuilder sql0 = new StringBuilder ("UPDATE I_ProductJP i ")
							.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||").append(DB.TO_STRING("Update Product: " + ex.toString()))
							.append("WHERE I_ProductJP_ID=").append(I_ProductJP_ID);
						DB.executeUpdate(sql0.toString(), get_TrxName());
						continue;
					}
					finally
					{
						DB.close(pstmt_updateProduct);
						pstmt_updateProduct = null;
					}
				}

				//	Do we have PO Info
				if (C_BPartner_ID != 0)
				{
					no = 0;
					//	If Product existed, Try to Update first
					if (!newProduct)
					{
						StringBuilder sqlt = new StringBuilder("UPDATE M_Product_PO ")
							.append("SET (IsCurrentVendor,C_UOM_ID,C_Currency_ID,UPC,")
							.append("PriceList,PricePO,RoyaltyAmt,PriceEffective,")
							.append("VendorProductNo,VendorCategory,Manufacturer,")
							.append("Discontinued,DiscontinuedBy, DiscontinuedAt, Order_Min,Order_Pack,")
							.append("CostPerOrder,DeliveryTime_Promised,Updated,UpdatedBy)= ")
							.append("(SELECT CAST('Y' AS CHAR),C_UOM_ID,C_Currency_ID,UPC,")    //jz fix EDB unknown datatype error
							.append("PriceList,PricePO,RoyaltyAmt,PriceEffective,")
							.append("VendorProductNo,VendorCategory,Manufacturer,")
							.append("Discontinued,DiscontinuedBy, DiscontinuedAt, Order_Min,Order_Pack,")
							.append("CostPerOrder,DeliveryTime_Promised,SysDate,UpdatedBy")
							.append(" FROM I_ProductJP")
							.append(" WHERE I_ProductJP_ID=").append(I_ProductJP_ID).append(") ")
							.append("WHERE M_Product_ID=").append(M_Product_ID).append(" AND C_BPartner_ID=").append(C_BPartner_ID);
						PreparedStatement pstmt_updateProductPO = DB.prepareStatement
							(sqlt.toString(), get_TrxName());
						//jz pstmt_updateProductPO.setInt(1, I_ProductJP_ID);
						// pstmt_updateProductPO.setInt(2, M_Product_ID);
						// pstmt_updateProductPO.setInt(3, C_BPartner_ID);
						try
						{
							no = pstmt_updateProductPO.executeUpdate();
							if (log.isLoggable(Level.FINER)) log.finer("Update Product_PO = " + no);
							noUpdatePO++;
						}
						catch (SQLException ex)
						{
							log.warning("Update Product_PO - " + ex.toString());
							noUpdate--;
							rollback();
							StringBuilder sql0 = new StringBuilder ("UPDATE I_ProductJP i ")
								.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||").append(DB.TO_STRING("Update Product_PO: " + ex.toString()))
								.append("WHERE I_ProductJP_ID=").append(I_ProductJP_ID);
							DB.executeUpdate(sql0.toString(), get_TrxName());
							continue;
						}
						finally
						{
							DB.close(pstmt_updateProductPO);
							pstmt_updateProductPO = null;
						}
					}
					if (no == 0)		//	Insert PO
					{
						pstmt_insertProductPO.setInt(1, M_Product_ID);
						pstmt_insertProductPO.setInt(2, C_BPartner_ID);
						pstmt_insertProductPO.setInt(3, I_ProductJP_ID);
						try
						{
							no = pstmt_insertProductPO.executeUpdate();
							if (log.isLoggable(Level.FINER)) log.finer("Insert Product_PO = " + no);
							noInsertPO++;
						}
						catch (SQLException ex)
						{
							log.warning("Insert Product_PO - " + ex.toString());
							noInsert--;			//	assume that product also did not exist
							rollback();
							StringBuilder sql0 = new StringBuilder ("UPDATE I_ProductJP i ")
								.append("SET I_IsImported='E', I_ErrorMsg=I_ErrorMsg||").append(DB.TO_STRING("Insert Product_PO: " + ex.toString()))
								.append("WHERE I_ProductJP_ID=").append(I_ProductJP_ID);
							DB.executeUpdate(sql0.toString(), get_TrxName());
							continue;
						}
					}
				}	//	C_BPartner_ID != 0

				//	Price List
				if (p_M_PriceList_Version_ID != 0)
				{
					BigDecimal PriceList = imp.getPriceList();
					BigDecimal PriceStd = imp.getPriceStd();
					BigDecimal PriceLimit = imp.getPriceLimit();
					if (PriceStd.signum() != 0 || PriceLimit.signum() != 0 || PriceList.signum() != 0)
					{
						MProductPrice pp = MProductPrice.get(getCtx(),
							p_M_PriceList_Version_ID, M_Product_ID, get_TrxName());
						if (pp == null)
							pp = new MProductPrice (getCtx(),
								p_M_PriceList_Version_ID, M_Product_ID, get_TrxName());
						pp.setPrices(PriceList, PriceStd, PriceLimit);
						ModelValidationEngine.get().fireImportValidate(this, imp, pp, ImportValidator.TIMING_AFTER_IMPORT);
						pp.saveEx();
					}
				}

				//	Update I_ProductJP
				pstmt_setImported.setInt(1, M_Product_ID);
				pstmt_setImported.setInt(2, I_ProductJP_ID);
				no = pstmt_setImported.executeUpdate();

				if(isRecordCommitJP)
				{
					commitEx();
				}
			}	//	for all I_ProductJP
		}
		catch (SQLException e)
		{
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null;pstmt = null;
			DB.close(pstmt_insertProductPO);
			pstmt_insertProductPO = null;
			DB.close(pstmt_setImported);
			pstmt_setImported = null;
		}

		//	Set Error to indicator to not imported
		sql = new StringBuilder ("UPDATE I_ProductJP ")
			.append("SET I_IsImported='N', Updated=SysDate ")
			.append("WHERE I_IsImported<>'Y'").append(clientCheck);
		no = DB.executeUpdate(sql.toString(), get_TrxName());

//		commitEx();
		long endImport = System.currentTimeMillis();
		addLog("インポート処理時間: " + (endImport - startImport) + " ミリ秒");


		/**
		 * indexの作成と制約の有効化の処理//TODO
		 */

		//indexの作成
		if(isDeleteIndexJP)
		{
			if (processMonitor != null)	processMonitor.statusUpdate("Indexの再作成");
			long startCreateIndex = System.currentTimeMillis();

			createIndex();
			commitEx();

			long endCreateIndex = System.currentTimeMillis();
			addLog("Indexの再作成時間: " + (endCreateIndex - startCreateIndex ) + " ミリ秒");
		}

		//制約の有効化
		if(isInvalidConstraintJP)
		{
			if (processMonitor != null)	processMonitor.statusUpdate("制約の有効化");
			long startValidConstraint = System.currentTimeMillis();

			validConstraint();
			commitEx();

			long endValidConstraint = System.currentTimeMillis();
			addLog("制約の有効化の処理時間: " + (endValidConstraint - startValidConstraint ) + " ミリ秒");
		}

		long finish = System.currentTimeMillis();

		addLog("*****処理が無事終了しました*****");
		addLog("合計処理時間: " + (finish - start) + " ミリ秒");

//		addLog (0, null, new BigDecimal (no), "@Errors@");
//		addLog (0, null, new BigDecimal (noInsert), "@M_Product_ID@: @Inserted@");
//		addLog (0, null, new BigDecimal (noUpdate), "@M_Product_ID@: @Updated@");
//		addLog (0, null, new BigDecimal (noInsertPO), "@M_Product_ID@ @Purchase@: @Inserted@");
//		addLog (0, null, new BigDecimal (noUpdatePO), "@M_Product_ID@ @Purchase@: @Updated@");
		return "";
	}	//	doIt


	@Override
	public String getImportTableName() {
		return X_I_ProductJP.Table_Name;
	}


	@Override
	public String getWhereClause() {
		StringBuilder msgreturn = new StringBuilder(" AND AD_Client_ID=").append(m_AD_Client_ID);
		return msgreturn.toString();
	}

	private boolean createIndex()
	{
		for(int i = 0; i < tables.length; i++)
		{
			MTable mTable = MTable.get(getCtx(), tables[i]);
			MTableIndex[] indexes = MTableIndex.get(mTable);
			for(int j = 0; j < indexes.length; j++)
			{
				String indexDropSql = indexes[j].getDDL();
				DB.executeUpdateEx(indexDropSql, get_TrxName());
//				addLog(0, null, new BigDecimal(rvalue), indexDropSql.toString());
			}
		}

		return true;
	}

	private boolean validConstraint()
	{
		for(int i = 0; i < tables.length; i++)
		{
			StringBuilder invalidConstraint = new StringBuilder (" update pg_trigger set tgenabled = 'O' "
											+ "where oid in (select tr.oid from pg_trigger tr INNER JOIN pg_class cl on (tr.tgrelid = cl.oid) WHERE cl.relname="
											+ "lower('" + tables[i] + "') )");

			DB.executeUpdate(invalidConstraint.toString(), get_TrxName());
		}
		return true;
	}

}	//	ImportProduct