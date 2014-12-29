/******************************************************************************
 * Copyright (C) 2013 Elaine Tan                                              *
 * Copyright (C) 2013 Trek Global
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/
package jpiere.base.plugin.factory;

import java.util.logging.Level;

import org.adempiere.base.ITaxProviderFactory;
import org.adempiere.base.equinox.EquinoxExtensionLocator;
import org.adempiere.model.ITaxProvider;
import org.compiere.util.CLogger;

/**
 * JPiere Tax Provider Factory
 *
 *
 * @author Hideaki Hagiwara
 * @version  $Id: JPiereTaxProviderFactory.java,v 1.0 2014/08/20
 *
 */
public class JPiereBasePluginTaxProviderFactory implements ITaxProviderFactory {

	private final static CLogger s_log = CLogger.getCLogger(JPiereBasePluginTaxProviderFactory.class);

	@Override
	public ITaxProvider newTaxProviderInstance(String className) {

		if(className.startsWith("jpiere.base.plugin")){


			ITaxProvider myCalculator = EquinoxExtensionLocator.instance().locate(ITaxProvider.class, className, null).getExtension();
			if (myCalculator == null)
			{
				//fall back to dynamic java class loading
				try
				{
					Class<?> ppClass = Class.forName(className);
					if (ppClass != null)
						myCalculator = (ITaxProvider) ppClass.newInstance();
				}
				catch (Error e1)
				{   //  NoClassDefFound
					s_log.log(Level.SEVERE, className + " - Error=" + e1.getMessage());
					return null;
				}
				catch (Exception e2)
				{
					s_log.log(Level.SEVERE, className, e2);
					return null;
				}
			}
			if (myCalculator == null)
			{
				s_log.log(Level.SEVERE, "Not found in extension registry and classpath");
				return null;
			}

			return myCalculator;
		}

		return null;
	}
}
