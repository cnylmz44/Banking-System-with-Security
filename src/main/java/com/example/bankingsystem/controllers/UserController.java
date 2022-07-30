package com.example.bankingsystem.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.bankingsystem.business.concretes.UserManager;
import com.example.bankingsystem.core.utilities.entities.LoginRequest;
import com.example.bankingsystem.core.utilities.entities.LoginResponse;
import com.example.bankingsystem.core.utilities.security.JWT.JWTTokenUtil;

@RestController
public class UserController {

	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private JWTTokenUtil jwtTokenUtil;
	@Autowired
	private UserManager userDetailsService;

	@PostMapping("/auth")
	public ResponseEntity<?> login(@RequestBody LoginRequest request) {

		try {
			authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
		} catch (BadCredentialsException e) {
			return ResponseEntity.badRequest().body("Bad credentials");
		} catch (DisabledException e) {
			return ResponseEntity.badRequest().body("Disabled Exception");
		}
		final UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());

		final String token = jwtTokenUtil.generateToken(userDetails);
		LoginResponse resp = new LoginResponse();
		resp.setStatus("success");
		resp.setToken(token);
		return ResponseEntity.ok().body(resp);
	}
}
