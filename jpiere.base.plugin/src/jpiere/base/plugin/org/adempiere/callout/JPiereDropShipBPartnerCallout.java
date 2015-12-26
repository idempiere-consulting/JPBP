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
package jpiere.base.plugin.org.adempiere.callout;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MBPartnerLocation;
import org.compiere.model.MOrder;
import org.compiere.model.MPriceList;
import org.compiere.model.X_C_Order;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;

/**
 *  JPiere Drop Ship BPartner CallOut
 *
 *  JPIERE-0143:JPBP
 *
 *  @author Hideaki Hagiwara(h.hagiwara@oss-erp.co.jp)
 *
 */
public class JPiereDropShipBPartnerCallout implements IColumnCallout {


	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab, GridField mField, Object value, Object oldValue) 
	{

		Integer C_BPartner_ID = (Integer)value;
		if (C_BPartner_ID == null || C_BPartner_ID.intValue() == 0)
			return "";
		
		boolean IsSOTrx = "Y".equals(Env.getContext(ctx, WindowNo, "IsSOTrx"));
		if(!IsSOTrx)
			return "";

		String sql = "SELECT bp.C_BPartner_ID,"
			+ "loc.C_BPartner_Location_ID,"
			+ "u.AD_User_ID "
			+ "FROM C_BPartner bp"
			+ " LEFT OUTER JOIN C_BPartner_Location loc ON (bp.C_BPartner_ID=loc.C_BPartner_ID AND loc.IsActive='Y')"
			+ " LEFT OUTER JOIN AD_User u ON (bp.C_BPartner_ID=u.C_BPartner_ID AND u.IsActive='Y') "
			+ "WHERE bp.C_BPartner_ID=? AND bp.IsActive='Y'";		//	#1

		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try
		{
			pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_BPartner_ID.intValue());
			rs = pstmt.executeQuery();
			if (rs.next())
			{
				
				if(mField.getColumnName().equals("C_BPartner_ID"))
				{
					if (C_BPartner_ID.intValue() == 0)
						mTab.setValue("DropShip_BPartner_ID", null);
					else
						mTab.setValue("DropShip_BPartner_ID", C_BPartner_ID);
				}
					
				int DropShip_Location_ID = rs.getInt(2);	
				if (DropShip_Location_ID == 0)
					mTab.setValue("DropShip_Location_ID", null);
				else
					mTab.setValue("DropShip_Location_ID", new Integer(DropShip_Location_ID));

				//	Contact - overwritten by InfoBP selection
				int DropShip_User_ID = rs.getInt(3);
				if (DropShip_User_ID == 0)
					mTab.setValue("DropShip_User_ID", null);
				else
					mTab.setValue("DropShip_User_ID", new Integer(DropShip_User_ID));
			}
		}
		catch (SQLException e)
		{
//			log.log(Level.SEVERE, "bPartnerBill", e);
			return e.getLocalizedMessage();
		}
		finally
		{
			DB.close(rs, pstmt);
			rs = null; pstmt = null;
		}
		return "";
	}


}
