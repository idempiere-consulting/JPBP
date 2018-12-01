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

import java.sql.ResultSet;
import java.util.Properties;

/**
 * JPIERE-0431:Contract Process Schedule
 *
 * @author Hideaki Hagiwara
 *
 */
public class MContractPSInvoiceLine extends X_JP_ContractPSInvoiceLine {

	public MContractPSInvoiceLine(Properties ctx, int JP_ContractPSInvoiceLine_ID, String trxName)
	{
		super(ctx, JP_ContractPSInvoiceLine_ID, trxName);
	}

	public MContractPSInvoiceLine(Properties ctx, ResultSet rs, String trxName)
	{
		super(ctx, rs, trxName);
	}

}