package com.dateroem.backend.dto;

// credential: the sign-in token from the website's "Continue with Google"
// button. phoneNo and userName: only on the second call, for someone new
// (see AuthService.googleSignIn) - the username is their own choice ("i
// dont want it to fill the username ... that is something one makes").
// phoneProof: with them, from /api/auth/phone/verify (the SMS code step).
public record GoogleSignInRequest(String credential, String phoneNo, String userName, String phoneProof) {}
