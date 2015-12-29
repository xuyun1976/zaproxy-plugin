package fr.novia.zaproxyplugin;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class ZAPUtil 
{
	public static boolean isLocalIP(String zapProxyHost)
	{
		if ("localhost".equalsIgnoreCase(zapProxyHost) || "127.0.0.1".equalsIgnoreCase(zapProxyHost))
			return true;
		
		try 
		{
			for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) 
			{
				NetworkInterface networkInterface = interfaces.nextElement();
	            
				if (networkInterface.isLoopback() || networkInterface.isVirtual() || !networkInterface.isUp()) 
					continue;
	                
				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
	            if (addresses.hasMoreElements() && addresses.nextElement().toString().equals(zapProxyHost)) 
	            	return true;
	        } 
		}
		catch (Exception e) 
		{
			e.printStackTrace();
	    }
		
	    return false;
	}
}
