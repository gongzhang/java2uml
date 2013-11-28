package co.gongzh.lab.java2uml;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.PlatformUI;
import org.eclipse.uml2.uml.Association;
import org.eclipse.uml2.uml.Class;
import org.eclipse.uml2.uml.Classifier;
import org.eclipse.uml2.uml.DataType;
import org.eclipse.uml2.uml.Generalization;
import org.eclipse.uml2.uml.Interface;
import org.eclipse.uml2.uml.Operation;
import org.eclipse.uml2.uml.Package;
import org.eclipse.uml2.uml.Parameter;
import org.eclipse.uml2.uml.Property;
import org.eclipse.uml2.uml.Type;
import org.eclipse.uml2.uml.UMLFactory;
import org.eclipse.uml2.uml.UMLPackage;
import org.eclipse.uml2.uml.VisibilityKind;

public class TransformCommand extends AbstractHandler implements IHandler {
	
	private Package currentModel;
	private final Map<IType, Classifier> classTrace = new HashMap<IType, Classifier>();
	private final Map<String, Type> typeTrace = new HashMap<String, Type>();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IJavaProject project = getSelectedProject();
		if (project != null) {
			try {
				currentModel = UMLFactory.eINSTANCE.createPackage();
				currentModel.setName(project.getProject().getName());
				List<IType> types = getTypes(project);
				
				// IType -> Class/Interface
				for (IType type : types) {
					Classifier c = toClassifier(type);
					currentModel.getPackagedElements().add(c);
				}
				
				// Generalization
				for (IType type : types) {
					Classifier c = toClassifier(type);
					generateGeneralization(c, type);
				}
				
				// Methods...
				for (IType type : types) {
					Classifier c = toClassifier(type);
					IMethod[] methods = type.getMethods();
					for (IMethod method : methods) {
						if (method.isConstructor()) continue;
						if (method.getElementName().startsWith("set")) continue;
						
						if (method.getElementName().startsWith("get") && method.getElementName().length() > 3) {
							String retTypeName = Signature.getSignatureSimpleName(method.getReturnType());
							if (retTypeName.contains("<")) {
								String stdName = retTypeName.substring(0, retTypeName.indexOf("<"));
								if (stdName.equals("List") ||
									stdName.equals("Set") ||
									stdName.equals("Collection")) {
									String paName = retTypeName.substring(retTypeName.indexOf("<") + 1, retTypeName.length() - 1);
									if (isClassOrInterface(paName)) {
										// Association *
										generateAssociation(c, method, true, toType(paName));
										continue;
									}
								}
							}
							if (isClassOrInterface(retTypeName)) {
								// Association 0..1
								generateAssociation(c, method, true, toType(retTypeName));
								continue;
							}
							generateProperty(c, method);
						} else {
							generateOperation(c, method);
						}
					}
				}
				
				// save model
				IFile file = project.getProject().getFile(project.getProject().getName() + ".uml");
				saveModelToFile(currentModel, file);
				
			} catch (JavaModelException ex) {
				throw new ExecutionException(ex.getMessage(), ex);
			} catch (IOException ex) {
				throw new ExecutionException(ex.getMessage(), ex);
			} finally {
				// clear
				classTrace.clear();
				typeTrace.clear();
				currentModel = null;
			}
		}
		return null;
	}
	
	private void generateAssociation(Classifier c, IMethod method, boolean multiple, Type type) throws JavaModelException {
		// end 1
		Property pr = UMLFactory.eINSTANCE.createProperty();
		pr.setName(toPropertyName(method.getElementName()));
		final int flags = method.getFlags();
		if (Flags.isPublic(flags)) {
			pr.setVisibility(VisibilityKind.PUBLIC_LITERAL);
		} else if (Flags.isProtected(flags)) {
			pr.setVisibility(VisibilityKind.PROTECTED_LITERAL);
		} else {
			pr.setVisibility(VisibilityKind.PRIVATE_LITERAL);
		}
		pr.setIsStatic(Flags.isStatic(flags));
		pr.setType(type);
		
		if (c instanceof Interface) {
			((Interface) c).getOwnedAttributes().add(pr);
		} else if (c instanceof Class) {
			((Class) c).getOwnedAttributes().add(pr);
		}
		
		if (multiple) {
			pr.setLower(0);
			pr.setUpper(-1);
		} else {
			pr.setLower(0);
			pr.setUpper(1);
		}
		
		// end 1
		Property pr2 = UMLFactory.eINSTANCE.createProperty();
		pr2.setType(c);
		
		if (type instanceof Interface) {
			((Interface) type).getOwnedAttributes().add(pr2);
		} else if (type instanceof Class) {
			((Class) type).getOwnedAttributes().add(pr2);
		}
		
		if (multiple) {
			pr.setLower(0);
			pr.setUpper(-1);
		} else {
			pr.setLower(0);
			pr.setUpper(1);
		}
		
		Association ass = UMLFactory.eINSTANCE.createAssociation();
		ass.getMemberEnds().add(pr);
		ass.getMemberEnds().add(pr2);
		currentModel.getPackagedElements().add(ass);
	}
	
	private boolean isClassOrInterface(String name) {
		return (toType(name) instanceof Classifier || toType(name) instanceof Interface) && !(toType(name) instanceof DataType);
	}
	
	private void generateGeneralization(Classifier c, IType type) throws JavaModelException {
		String superType = Signature.getSignatureSimpleName(type.getSuperclassTypeSignature());
		if (toType(superType) instanceof Classifier) {
			Generalization gen = UMLFactory.eINSTANCE.createGeneralization();
			gen.setGeneral((Classifier) toType(superType));
			if (c instanceof Interface) {
				((Interface) c).getGeneralizations().add(gen);
			} else if (c instanceof Class) {
				((Class) c).getGeneralizations().add(gen);
			}
		}
		for (String signature : type.getSuperInterfaceTypeSignatures()) {
			String interf = Signature.getSignatureSimpleName(signature);
			if (toType(interf) instanceof Classifier) {
				Generalization gen = UMLFactory.eINSTANCE.createGeneralization();
				gen.setGeneral((Classifier) toType(interf));
				if (c instanceof Interface) {
					((Interface) c).getGeneralizations().add(gen);
				} else if (c instanceof Class) {
					((Class) c).getGeneralizations().add(gen);
				}
			}
		}
	}
	
	private Property generateProperty(Classifier c, IMethod method) throws JavaModelException {
		Property pr = UMLFactory.eINSTANCE.createProperty();
		pr.setName(toPropertyName(method.getElementName()));
		final int flags = method.getFlags();
		if (Flags.isPublic(flags)) {
			pr.setVisibility(VisibilityKind.PUBLIC_LITERAL);
		} else if (Flags.isProtected(flags)) {
			pr.setVisibility(VisibilityKind.PROTECTED_LITERAL);
		} else {
			pr.setVisibility(VisibilityKind.PRIVATE_LITERAL);
		}
		pr.setIsStatic(Flags.isStatic(flags));
		pr.setType(toType(Signature.getSignatureSimpleName(method.getReturnType())));
		
		if (c instanceof Interface) {
			((Interface) c).getOwnedAttributes().add(pr);
		} else if (c instanceof Class) {
			((Class) c).getOwnedAttributes().add(pr);
		}
		return pr;
	}
	
	private String toPropertyName(String methodName) {
		StringBuilder str = new StringBuilder(methodName.substring(3));
		str.setCharAt(0, Character.toLowerCase(str.charAt(0)));
		return str.toString();
	}
	
	private void generateOperation(Classifier c, IMethod method) throws JavaModelException {
		Operation op = UMLFactory.eINSTANCE.createOperation();
		op.setName(method.getElementName());
		final int flags = method.getFlags();
		if (Flags.isPublic(flags)) {
			op.setVisibility(VisibilityKind.PUBLIC_LITERAL);
		} else if (Flags.isProtected(flags)) {
			op.setVisibility(VisibilityKind.PROTECTED_LITERAL);
		} else {
			op.setVisibility(VisibilityKind.PRIVATE_LITERAL);
		}
		op.setIsStatic(Flags.isStatic(flags));
		op.setType(toType(Signature.getSignatureSimpleName(method.getReturnType())));
		for (ILocalVariable var : method.getParameters()) {
			Parameter para = UMLFactory.eINSTANCE.createParameter();
			para.setName(var.getElementName());
			para.setType(toType(Signature.getSignatureSimpleName(var.getTypeSignature())));
			op.getOwnedParameters().add(para);
		}
		if (c instanceof Interface) {
			((Interface) c).getOwnedOperations().add(op);
		} else if (c instanceof Class) {
			((Class) c).getOwnedOperations().add(op);
		}
	}
	
	private Type toType(String name) {
		if (name.isEmpty()) return null;
		if (name.equals("void")) return null;
		if (typeTrace.containsKey(name)) return typeTrace.get(name);
		for (Classifier c : classTrace.values()) {
			if (c.getName().equals(name)) {
				typeTrace.put(name, c);
				return c;
			}
		}
		DataType dt = UMLFactory.eINSTANCE.createDataType();
		dt.setName(name);
		typeTrace.put(name, dt);
		currentModel.getPackagedElements().add(dt);
		return dt;
	}

	private IJavaProject getSelectedProject() {
		ISelection sel = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService().getSelection();
		if (!sel.isEmpty() && sel instanceof IStructuredSelection) {
			IAdaptable obj = (IAdaptable) ((IStructuredSelection) sel).getFirstElement();
			IJavaProject project = (IJavaProject) obj.getAdapter(IJavaProject.class);
			return project;
		} else {
			return null;
		}
	}

	private List<IType> getTypes(IJavaProject javaProject) throws JavaModelException {
		List<IType> rst = new ArrayList<IType>();
		IPackageFragment[] packages = javaProject.getPackageFragments();
		for (IPackageFragment mypackage : packages) {
			if (mypackage.getKind() == IPackageFragmentRoot.K_SOURCE) {
				for (ICompilationUnit unit : mypackage.getCompilationUnits()) {
					IType[] allTypes = unit.getAllTypes();
					for (IType type : allTypes) {
						rst.add(type);
					}
				}
			}
		}
		return rst;
	}
	
	private Classifier toClassifier(IType type) throws JavaModelException {
		if (classTrace.containsKey(type)) return classTrace.get(type);
		Classifier c;
		if (type.isInterface()) c = UMLFactory.eINSTANCE.createInterface();
		else c = UMLFactory.eINSTANCE.createClass();
		c.setName(type.getElementName());
		c.setIsAbstract(Flags.isAbstract(type.getFlags()));
		classTrace.put(type, c);
		return c;
	}
	
	private void saveModelToFile(EObject model, IResource file) throws IOException {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getPackageRegistry().put(UMLPackage.eNS_URI, UMLPackage.eINSTANCE);
		Resource resource = resourceSet.createResource(URI.createPlatformResourceURI(file.getFullPath().toString(), false));
		Map<String, String> options = new HashMap<String, String>();
		options.put(XMIResource.OPTION_ENCODING, "UTF-8");
		resource.getContents().add(model); // add current model to resource
		resource.save(options);
	}

}
