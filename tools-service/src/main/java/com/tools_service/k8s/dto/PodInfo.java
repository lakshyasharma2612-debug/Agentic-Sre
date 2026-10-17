package com.tools_service.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor 
@Getter 
@ToString 
public class PodInfo {
String name;
String status;
}
