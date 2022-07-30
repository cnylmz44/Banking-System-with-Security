package com.example.bankingsystem.business.concretes;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.example.bankingsystem.core.utilities.entities.BankUser;

@Component
public class UserManager implements UserDetailsService {

	// Connect to Database
	// Select User
	// Set User
	// Create Bank User
	// Set User to Bank User
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		try {
			Class.forName("com.mysql.cj.jdbc.Driver");
			Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/bankingsystem?useSSL=false", "root",
					"1234");
			String query = "SELECT * FROM users WHERE username = \"" + username + "\"";
			Statement st = c.createStatement();
			ResultSet rs = st.executeQuery(query);
			if (rs.next()) {
				String id = rs.getString("id");
				String name = rs.getString("username");
				String password = rs.getString("password");
				String authoritiesString = rs.getString("authorities");
				String[] parts = authoritiesString.split(",");
				List<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
				for (String authority : parts) {
					SimpleGrantedAuthority sga = new SimpleGrantedAuthority(authority);
					authorities.add(sga);
				}

				BankUser bankUser = new BankUser(id, name, password, true, true, true, true, authorities);

				return bankUser;
			} else {
				throw new UsernameNotFoundException(username + " Not Found");
			}

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
